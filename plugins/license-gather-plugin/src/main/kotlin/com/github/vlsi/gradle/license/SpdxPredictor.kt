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

import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.text
import java.io.*
import java.lang.IllegalStateException

val spdxPredictor by lazy {
    SpdxLicense::class.java
        .getResourceAsStream("models/tfidf_licenses.bin")
        .use { it.buffered().loadModel().predictor() }
}

private fun InputStream.loadModel(): Model<SpdxLicense> =
    Model.load(DataInputStream(this)) { SpdxLicense.fromId(it) }

fun main(args: Array<String>) {
    val model =
        TfIdfBuilder<SpdxLicense>().apply {
            SpdxLicense.values()
                .forEach {
                    addDocument(
                        it,
                        it.text
                    )
                }
        }.toModel()

    val array = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use {
            model.writeTo(it) { doc -> doc.id }
        }
        baos.toByteArray()
    }

    val loadModel = array.inputStream().loadModel()

    if (loadModel != model) {
        throw IllegalStateException("Model.load produces different result from the original one")
    }

    File(args[0]).apply {
        absoluteFile.parentFile.mkdirs()
        writeBytes(array)
    }
}
