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
package com.github.vlsi.jandex

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException

open class JandexException(message: String?, cause: Throwable?) : Exception(message, cause)

class JandexFileParseException(val file: java.io.File, message: String?, cause: Throwable?) :
    JandexException(message, cause)

interface JandexWorkParameters : WorkParameters {
    val maxErrors: Property<Int>
    val inputFiles: ConfigurableFileCollection
    val indexFile: RegularFileProperty
    val jandexBuildAction: Property<JandexBuildAction>
}

abstract class JandexWork : WorkAction<JandexWorkParameters> {
    companion object {
        val logger = LoggerFactory.getLogger(JandexWork::class.java)
    }

    override fun execute() {
        val start = System.currentTimeMillis()
        val indexerClass = Class.forName("org.jboss.jandex.Indexer")
        val indexer = indexerClass.getConstructor().newInstance()
        val indexMethod = indexerClass.getMethod("index", InputStream::class.java)
        var indexedFiles = 0
        val maxErrors = parameters.maxErrors.get()
        val errors = mutableListOf<JandexFileParseException>()
        for (file in parameters.inputFiles) {
            logger.debug("Jandex: processing {}", file)
            file.inputStream().use {
                try {
                    indexMethod.invoke(indexer, it)
                } catch (e: InvocationTargetException) {
                    val cause = e.cause
                    logger.info("Error indexing file {}: {}", file, cause?.message)
                    errors += JandexFileParseException(
                        file,
                        file.toString(),
                        cause
                    )
                }
            }
            indexedFiles += 1
            if (errors.size > maxErrors) {
                break
            }
        }
        if (errors.isNotEmpty()) {
            val s = if (errors.size == 1) "" else "s"
            throw JandexException(
                "Unable to process ${errors.size} file$s" +
                        ". It might be caused by invalid bytecode in the class file or a defect in org.jboss:jandex" +
                        "; You might analyze the bytecode with the following command: javap -verbose -p <ClassName.class>" +
                        "; Unable to parse the following file$s: " +
                        errors.map { it.file }.sorted().joinToString(", "),
                if (errors.size != 1) null else errors.first().cause
            ).apply {
                if (errors.size > 1) {
                    for (e in errors) {
                        addSuppressed(e)
                    }
                }
            }
        }
        val parseFiles = System.currentTimeMillis()
        val index = try {
            indexerClass.getMethod("complete").invoke(indexer)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
        val indexBuild = System.currentTimeMillis()
        writeIndex(index)
        val indexWrite = System.currentTimeMillis()
        logger.info(
            "Jandex indexed $indexedFiles files, total time: ${indexWrite - start} ms" +
                    ", class processing: ${parseFiles - start} ms" +
                    ", index build: ${indexBuild - parseFiles} ms" +
                    ", index write: ${indexWrite - indexBuild} ms"
        )
    }

    private fun writeIndex(index: Any?) {
        val indexFile = parameters.indexFile.get().asFile
        if (index == null || parameters.jandexBuildAction.get() == JandexBuildAction.VERIFY_ONLY) {
            logger.debug(
                "Writing empty jandex index to {} (indexBuildAction=VERIFY_ONLY" +
                        ", so the file is used only for Gradle build cache)",
                indexFile
            )
            indexFile.writeBytes(ByteArray(0))
            return
        }
        logger.debug("Writing jandex index to {}", indexFile)
        indexFile.absoluteFile.parentFile.mkdirs()
        indexFile.outputStream().use { indexFileStream ->
            val indexWriterClass = Class.forName("org.jboss.jandex.IndexWriter")
            val indexWriter = indexWriterClass.getConstructor(OutputStream::class.java)
                .newInstance(indexFileStream)
            val writeMethod = indexWriterClass.getMethod("write", index.javaClass)
            try {
                writeMethod.invoke(indexWriter, index)
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                throw JandexException(
                    "Unable to write index to $indexFile. It is likely caused by a defect in org.jboss:jandex. $cause",
                    cause
                )
            }
        }
    }
}
