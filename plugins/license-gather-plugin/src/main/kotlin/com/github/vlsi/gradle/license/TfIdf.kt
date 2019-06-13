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

import kotlin.math.ln
import kotlin.math.sqrt

typealias Term = String

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

    fun build(): Predictor<Document> {
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

        val termList = freqTerms.toList()
        val docVec = mutableMapOf<Document, DoubleArray>()
        for (doc in documents.entries) {
            val terms = termList
                .map {
                    doc.value.getOrDefault(it, 0) * idf.getValue(it)
                }
                .toDoubleArray()
            val k = 1 / sqrt(terms.sumByDouble { it * it })
            for (i in terms.indices) {
                terms[i] *= k
            }
            docVec[doc.key] = terms
        }

        val usedIdf =
            freqTerms.associateWith { idf[it] ?: error("Term $it is not found in documents") }
        return Predictor(tokenizer, usedIdf, docVec)
    }
}

private fun cross(a: DoubleArray, b: DoubleArray): Double {
    var sum = 0.0
    for (i in a.indices) {
        sum += a[i] * b[i]
    }
    return sum
}

class Tokenizer {
    companion object {
        val WHITESPACE = Regex("(\\s\\.|[^\\p{L}\\d-.]|v(?=\\d)|\\.(?=\\s))++")

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
        val text = input.replace(Regex("<[^>]*+>"), " ").toLowerCase()

        val words = WHITESPACE.split(text)
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
    private val idf: Map<Term, Double>,
    private val docVec: Map<Document, DoubleArray>
) {
    fun predict(text: String): Map<Document, Double> {
        val testTerms =
            tokenizer.getTokens(text)
                .groupingBy { it }
                .eachCount()

        val testVec = idf
            .map {
                testTerms.getOrDefault(it.key, 0) * it.value
            }
            .toDoubleArray()
        val norm = 1 / sqrt(cross(testVec, testVec))

        return docVec.mapValues { cross(it.value, testVec) * norm }
    }
}
