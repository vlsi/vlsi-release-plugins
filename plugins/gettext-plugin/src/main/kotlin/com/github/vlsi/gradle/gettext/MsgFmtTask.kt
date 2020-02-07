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
package com.github.vlsi.gradle.gettext

import org.gradle.api.GradleException
import org.gradle.api.file.FileType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

open class MsgFmtTask @Inject constructor(
    objects: ObjectFactory
) : BaseGettextTask(objects) {
    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NONE)
    val poFiles = objects.fileCollection()

    @Input
    val args = objects.listProperty<String>()

    @OutputDirectory
    val outputDir = objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("gettext/$name/po"))

    @Internal
    val tmpDir = objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("gettext/$name/tmp"))

    @Input
    val targetBundle = objects.property<String>()

    @Input
    val format = objects.property<OutputFormat>().convention(OutputFormat.JAVA)

    @Input
    val escapeUnicode = objects.property<Boolean>().convention(false)

    @Input
    val encoding = objects.property<String>().convention("UTF-8")

    init {
        executable.convention(
                format.map {
                    if (it == OutputFormat.PROPERTIES) "msgcat" else "msgfmt"
                }
        )
    }

    @TaskAction
    fun run(inputChanges: InputChanges) {
        val outDir = outputDir.get().asFile
        val tmpDir = tmpDir.get().asFile
        val cmd = executable.get()
        val arg = args.get()
        val targetBundle = targetBundle.get()
        for (po in inputChanges.getFileChanges(poFiles)) {
            if (po.fileType != FileType.FILE) {
                continue
            }

            val locale = po.file.nameWithoutExtension.toJavaLocale()

            val outputName = when (format.get()) {
                OutputFormat.JAVA -> targetBundle.replace('.', File.separatorChar) + "_" + locale + ".java"
                OutputFormat.PROPERTIES -> TODO()
            }
            val outFile = File(outDir, outputName)
            if (po.changeType == ChangeType.REMOVED) {
                logger.debug("Removing output {}", outFile)
                project.delete(outFile)
                continue
            }
            logger.debug("Processing {} with {} {}", po.file, cmd, arg)
            project.delete(tmpDir)
            tmpDir.mkdirs()
            project.exec {
                executable = cmd
                if (format.get() == OutputFormat.JAVA) {
                    args("--java2")
                    args("--source")
                    args("-d", tmpDir)
                    args("-r", targetBundle)
                    args("-l", locale)
                    args(po.file.absolutePath)
                } else {
                    TODO(format.get().toString() + " is not supported yet")
                }
            }
            ant.withGroovyBuilder {
                "move"("todir" to outDir.absolutePath) {
                    "fileset"("dir" to tmpDir.absolutePath)
                }
            }
            if (!escapeUnicode.get()) {
                outFile.unescapeUnicode(Charset.forName(encoding.get()))
            }
        }
    }

    private fun String.toJavaLocale(): String {
        val tokens = split('_').toMutableList()
        if (tokens.size < 1 || tokens.size > 3) {
            throw GradleException("Invalid locale format: $this")
        }
        if (tokens.size < 3) {
            val lastToken = tokens.last()
            val variants = lastToken.split('@', limit = 2)
            if (variants.size > 1) {
                tokens.removeAt(tokens.lastIndex)
                tokens.add(variants[0])
                tokens.add("")
                tokens.add(variants[1])
            }
        }
        if (tokens.first().equals("he", ignoreCase = true)) {
            tokens[0] = "iw"
        } else if (tokens.first().equals("yi", ignoreCase = true)) {
            tokens[0] = "ji"
        } else if (tokens.first().equals("id", ignoreCase = true)) {
            tokens[0] = "in"
        }
        return tokens.joinToString("_")
    }

    fun File.unescapeUnicode(charset: Charset) {
        val contents = readText(charset)
        bufferedWriter(charset).use { w ->
            val length = contents.length
            var i = 0
            while (i < length) {
                val c = contents[i]
                i += if (c != '\\' || i + 1 >= length || contents[i + 1] != 'u') {
                    w.append(c)
                    1
                } else {
                    val code = contents.substring(i + 2, i + 6)
                    w.write(code.toInt(16))
                    6
                }
            }
        }
    }
}
