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
package com.github.vlsi.gradle.git

import org.apache.tools.ant.util.ReaderInputStream
import org.eclipse.jgit.util.io.AutoCRLFInputStream
import org.eclipse.jgit.util.io.AutoLFInputStream
import org.gradle.api.GradleException
import java.io.FilterReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private val BYTE_SAFE_ENCODING = StandardCharsets.ISO_8859_1

private fun Reader.decode(): InputStream {
    if (this is InputStreamReader) {
        if (encoding != BYTE_SAFE_ENCODING.name() &&
            encoding != "ISO8859_1" &&
            Charset.forName(encoding) != BYTE_SAFE_ENCODING
        ) {
            throw GradleException("Expecting encoding $BYTE_SAFE_ENCODING got $encoding")
        }
    }
    return ReaderInputStream(this, BYTE_SAFE_ENCODING.name())
}

private fun InputStream.encode() = reader(BYTE_SAFE_ENCODING)

private fun filterCrLf(reader: Reader, detectBinary: Boolean) =
    AutoCRLFInputStream(reader.decode(), detectBinary).encode()

private fun filterLf(reader: Reader, detectBinary: Boolean) =
    AutoLFInputStream(reader.decode(), detectBinary).encode()

class FilterAutoCrlf(reader: Reader) : FilterReader(filterCrLf(reader, detectBinary = true))
class FilterAutoLf(reader: Reader) : FilterReader(filterLf(reader, detectBinary = true))
class FilterTextCrlf(reader: Reader) : FilterReader(filterCrLf(reader, detectBinary = false))
class FilterTextLf(reader: Reader) : FilterReader(filterLf(reader, detectBinary = false))
