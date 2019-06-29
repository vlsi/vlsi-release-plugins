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

import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

val LICENSE_FILES =
    listOf("LICENSE", "NOTICE", "COPYING", "COPYING.LESSER")

fun looksLiceLicense(name: String) =
    LICENSE_FILES.any { name.startsWith(it, ignoreCase = true) }

private fun String.looksLikeLicense() =
    !endsWith(".class") && looksLiceLicense(substringAfterLast('/'))

class FindLicense @Inject constructor(
    private val id: String,
    private val file: File,
    private val outputDir: File
) : Runnable {

    override fun run() {
        if (file.extension !in arrayOf("zip", "jar")) {
            return
        }

        // TODO: allow customization of encoding
        ZipFile(file).use { zipFile ->
            for (e in zipFile.entries()) {
                val entryName = e.name
                if (e.isDirectory || !entryName.looksLikeLicense()
                    || entryName.contains("../")
                ) {
                    continue
                }
                val outFile = File(outputDir, entryName.removePrefix("/").removePrefix("META-INF/"))
                outFile.parentFile.mkdirs()
                outFile.outputStream().use { out ->
                    zipFile.getInputStream(e).use {
                        it.copyTo(out)
                    }
                }
            }
        }
    }
}