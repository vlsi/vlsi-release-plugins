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
package com.github.vlsi.gradle.checksum

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

internal fun File.loadProperties(): Properties =
    Properties().apply {
        if (exists()) {
            inputStream().use {
                load(it)
            }
        }
    }

internal fun Properties.saveTo(file: File, comments: String) {
    file.parentFile.mkdirs()
    file.outputStream().use {
        SortedProperties(this).store(it, comments)
    }
}

internal fun File.sha512(): String {
    val md = MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
        .padStart(128, '0')
}
