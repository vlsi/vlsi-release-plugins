/*
 * Copyright 2019 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.vlsi.gradle.license

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.ln
import kotlin.math.sqrt

typealias Term = String
typealias TermId = Int

class TfIdfBuilder<Document> {
    private val tokenizer = Tokenizer()

    private val words =
        mutableMapOf<Term, MutableSet<Document>>()
    private val documents = mutableMapOf<Document, Map<Term, Int>>()

    fun addDocument(name: Document, text: String) {
        val tokens = tokenizer.getTokens(text)
        documents[name] = tokens.groupingBy { it }.eachCount()
        for (w in tokens) {
            words.computeIfAbsent(w) { mutableSetOf() }.add(name)
        }
    }

    fun toModel(): Model<Document> {
        val idf: Map<Term, Double> =
            words.mapValues { ln(documents.size.toDouble() / it.value.size) }
        val freqTerms = mutableSetOf<Term>()
        for (doc in documents.entries) {
            val cnt = mutableMapOf<Int, Int>()
            val terms = doc.value.entries
                .asSequence()
                .map { it.key to (it.value * idf.getValue(it.key)) }
                .sortedByDescending { it.second }
                .filter {
                    val c = it.first.count { c -> c == ' ' }
                    cnt.compute(c) { _, v -> (v ?: 0) + 1 } ?: 0 <= 5
                }
                .take(20)
                .toList()
            freqTerms.addAll(terms.map { it.first })
        }

        val termList = freqTerms.toList().sorted()
        val docVec = mutableMapOf<Document, MutableMap<TermId, Double>>()
        for ((document, termCount) in documents.entries) {
            val terms = termList
                .asSequence()
                .withIndex()
                .filter { termCount.containsKey(it.value) }
                .associateTo(mutableMapOf()) { (index, term) ->
                    index to termCount.getValue(term) * idf.getValue(term)
                }
            val k = 1 / sqrt(terms.values.sumOf { it * it })
            terms.replaceAll { _, value -> value * k }
            docVec[document] = terms
        }

        val usedIdf =
            termList.asSequence()
                .withIndex()
                .associate { (index, value) -> index to idf.getOrDefault(value, 0.0) }
        return Model(termList, usedIdf, docVec)
    }

    fun build(): Predictor<Document> =
        toModel().predictor()
}

class Model<Document>(
    private val terms: List<Term>,
    private val idf: Map<TermId, Double>,
    private val docVec: Map<Document, Map<TermId, Double>>
) {
    companion object {
        private const val FORMAT_ID = 1

        fun <Document> load(
            dis: DataInputStream,
            deserializeDocument: (String) -> Document
        ): Model<Document> {
            val formatId = dis.readInt()
            if (formatId != FORMAT_ID) {
                throw IllegalArgumentException("Invalid file format. Expecting $FORMAT_ID got $formatId")
            }
            val terms = mutableListOf<String>()
            val idf = mutableMapOf<TermId, Double>()
            val docVec = mutableMapOf<Document, Map<TermId, Double>>()
            val termsSize = dis.readShort().toInt()
            repeat(termsSize) {
                terms.add(dis.readUTF())
            }
            repeat(termsSize) {
                idf[it] = dis.readDouble()
            }
            repeat(dis.readShort().toInt()) {
                val docId = dis.readUTF()
                val docTerms = mutableMapOf<TermId, Double>()
                repeat(dis.readShort().toInt()) {
                    val termId = dis.readShort()
                    docTerms[termId.toInt()] = dis.readDouble()
                }
                docVec[deserializeDocument(docId)] = docTerms
            }
            return Model(terms, idf, docVec)
        }
    }

    private val termIds =
        terms.asSequence()
            .withIndex()
            .associate { it.value to it.index }

    init {
        require(terms.size == idf.size) { "terms.size should be equal to idf.size: ${terms.size} != ${idf.size}" }
        require(terms.size < Short.MAX_VALUE) { "terms.size should be less than ${Short.MAX_VALUE}" }
    }

    fun writeTo(os: DataOutputStream, serializeDocument: (Document) -> String) {
        os.writeInt(FORMAT_ID) // file format
        os.writeShort(terms.size)
        for (term in terms) {
            os.writeUTF(term)
        }
        for ((_, value) in idf) {
            os.writeDouble(value)
        }
        os.writeShort(docVec.size)
        for ((doc, values) in docVec) {
            os.writeUTF(serializeDocument(doc))
            os.writeShort(values.size)
            for ((index, value) in values) {
                os.writeShort(index)
                os.writeDouble(value)
            }
        }
    }

    fun predictor(): Predictor<Document> =
        Predictor(Tokenizer(), termIds, idf, docVec)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Model<*>

        if (terms != other.terms) return false
        if (idf != other.idf) return false
        if (docVec != other.docVec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = terms.hashCode()
        result = 31 * result + idf.hashCode()
        result = 31 * result + docVec.hashCode()
        return result
    }
}

class Tokenizer {
    companion object {
        val WHITESPACE = Regex("""(\s\.|[^\p{L}\d-.]|v(?=\d)|\.(?=\s))++""")
        val COPYRIGHT = Regex("""((copyright|©|\(c\))++\s*+)++""")
        val STOP_WORDS = Regex("[-.]++")
        val SPDX_TAG = Regex("<[^>]*+>")

        val NORMALIZE = mapOf(
            "acknowledgment" to "acknowledgement",
            "analogue" to "analog",
            "analyse" to "analyze",
            "artefact" to "artifact",
            "authorisation" to "authorization",
            "authorised" to "authorized",
            "calibre" to "caliber",
            "cancelled" to "canceled",
            "apitalisations" to "apitalizations",
            "catalogue" to "catalog",
            "categorise" to "categorize",
            "centre" to "center",
            "emphasised" to "emphasized",
            "favour" to "favor",
            "favourite" to "favorite",
            "fulfil" to "fulfill",
            "fulfilment" to "fulfillment",
            "initialise" to "initialize",
            "judgment" to "judgement",
            "labelling" to "labeling",
            "labour" to "labor",
            "licence" to "license",
            "maximise" to "maximize",
            "modelled" to "modeled",
            "modelling" to "modeling",
            "offence" to "offense",
            "optimise" to "optimize",
            "organisation" to "organization",
            "organise" to "organize",
            "practise" to "practice",
            "programme" to "program",
            "realise" to "realize",
            "recognise" to "recognize",
            "signalling" to "signaling",
            "utilisation" to "utilization",
            "whilst" to "while",
            "wilful" to "wilfull",
            "non-ommercial" to "noncommercial",
            "copyright-owner" to "copyright-holder",
            "sublicense" to "sub-license",
            "non-infringement" to "noninfringement",
            "©" to "copyright",
            "(c)" to "copyright",
            "\"" to "'"
        )
    }

    fun getTokens(input: String): List<Term> {
        val text = input
            .replace(SPDX_TAG, " ")
            .lowercase()
            .replace(COPYRIGHT, "copyright ")

        val words = WHITESPACE.split(text)
            .asSequence()
            .filter { it.isNotEmpty() && !STOP_WORDS.matches(it) }
            .map { NORMALIZE[it] ?: it }
            .minus(setOf("the", "version", "software"))

        val x = words
            .plus(words.windowed(2, partialWindows = false) { it.joinToString(" ") })
            .plus(words.windowed(3, partialWindows = false) { it.joinToString(" ") })
            .toList()
        return x
    }
}

class Predictor<Document>(
    private val tokenizer: Tokenizer,
    private val terms: Map<Term, TermId>,
    private val idf: Map<TermId, Double>,
    private val docVec: Map<Document, Map<TermId, Double>>
) {
    fun predict(text: String): Map<Document, Double> {
        val testTerms =
            tokenizer.getTokens(text)
                .asSequence()
                .mapNotNull { terms[it] }
                .groupingBy { it }
                .eachCount()

        val testVec =
            testTerms
                .mapValuesTo(mutableMapOf()) { (termId, termCount) ->
                    termCount * idf.getOrDefault(
                        termId,
                        0.0
                    )
                }

        val norm = 1 / sqrt(testVec.values.sumOf { it * it })
        testVec.replaceAll { _, value -> value * norm }

        return docVec.mapValues { (_, docTerms) -> cross(testVec, docTerms) }
    }
}

private fun cross(a: Map<TermId, Double>, b: Map<TermId, Double>): Double {
    if (a.size > b.size) {
        return cross(b, a)
    }
    var sum = 0.0
    for ((termId, value) in a) {
        val bValue = b.getOrDefault(termId, 0.0)
        sum += value * bValue
    }
    return sum
}
