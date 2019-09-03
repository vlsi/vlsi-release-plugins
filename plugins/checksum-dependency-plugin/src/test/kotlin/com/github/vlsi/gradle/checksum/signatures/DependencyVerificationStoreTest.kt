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
package com.github.vlsi.gradle.checksum.signatures

import com.github.vlsi.gradle.checksum.model.DependencyVerificationStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringWriter

val CHECKSUMS = """
    <?xml version='1.0' encoding='utf-8'?>
    <dependency-verification version="1">
        <trust-requirement pgp="GROUP" checksum="NONE"/>
        <trusted-keys>
            <trusted-key id="cafebabecafebabe" group="org.jetbrains"/>
            <trusted-key id="bcf4173966770193" group="org.jetbrains"/>
            <trusted-key id="379ce192d401ab61" group="org.jetbrains.intellij.deps"/>
        </trusted-keys>
        <dependencies>
            <dependency group="com.android.tools" module="dvlib" version="24.0.0">
                <sha512>BF96E53408EAEC8E366F50E0125D6E</sha512>
                <sha512>239789823479823497823497234978</sha512>
                <pgp>ac214caa0612b399</pgp>
                <pgp>bcf4173966770193</pgp>
            </dependency>
            <dependency group="com.android.tools" module="dvlib" version="24.0.0" classifier="shaonly">
                <sha512>239789823479823497823497234978</sha512>
            </dependency>
            <dependency group="com.android.tools" module="dvlib" version="24.0.0" classifier="pgponly" extension="tar">
                <pgp>ac214caa0612b399</pgp>
            </dependency>
            <dependency group="com.android.tools" module="dvlib" version="24.0.0" classifier="any"/>
        </dependencies>
    </dependency-verification>
    """.trimIndent()

private fun String.normalizeNl() = replace(Regex("[\r\n]+"), "\n")

class DependencyVerificationStoreTest {
    @Test
    internal fun load() {
        val res = DependencyVerificationStore.load(
            CHECKSUMS.byteInputStream(),
            "checksum.xml"
        )
        Assertions.assertEquals(
            """
                DependencyVerification(ignoredKeys=[],
                trustedKeys={org.jetbrains=[bcf4173966770193,
                cafebabecafebabe],
                org.jetbrains.intellij.deps=[379ce192d401ab61]},
                dependencies={com.android.tools:dvlib:24.0.0=DependencyChecksum(sha512=[BF96E53408EAEC8E366F50E0125D6E,
                239789823479823497823497234978],
                pgpKeys=[ac214caa0612b399,
                bcf4173966770193],
                com.android.tools:dvlib:24.0.0:shaonly=DependencyChecksum(sha512=[239789823479823497823497234978],
                pgpKeys=[],
                com.android.tools:dvlib:24.0.0:pgponly@tar=DependencyChecksum(sha512=[],
                pgpKeys=[ac214caa0612b399],
                com.android.tools:dvlib:24.0.0:any=DependencyChecksum(sha512=[],
                pgpKeys=[]})
            """.trimIndent().normalizeNl(),
            res.toString().normalizeNl().replace(", ", ",\n")
        )
    }

    @Test
    internal fun store() {
        val loaded = DependencyVerificationStore.load(
            CHECKSUMS.byteInputStream(),
            "checksum.xml"
        )
        val res = StringWriter().use {
            DependencyVerificationStore.save(it, loaded)
            it.toString()
        }
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='1'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='bcf4173966770193' group='org.jetbrains' />
                <trusted-key id='cafebabecafebabe' group='org.jetbrains' />
                <trusted-key id='379ce192d401ab61' group='org.jetbrains.intellij.deps' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.android.tools' module='dvlib' version='24.0.0'>
                  <pgp>ac214caa0612b399</pgp>
                  <pgp>bcf4173966770193</pgp>
                  <sha512>239789823479823497823497234978</sha512>
                  <sha512>BF96E53408EAEC8E366F50E0125D6E</sha512>
                </dependency>
                <dependency group='com.android.tools' module='dvlib' version='24.0.0' classifier='any' />
                <dependency group='com.android.tools' module='dvlib' version='24.0.0' classifier='pgponly' extension='tar'>
                  <pgp>ac214caa0612b399</pgp>
                </dependency>
                <dependency group='com.android.tools' module='dvlib' version='24.0.0' classifier='shaonly'>
                  <sha512>239789823479823497823497234978</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
        """.trimIndent().normalizeNl() + "\n", res.normalizeNl()
        )
    }
}
