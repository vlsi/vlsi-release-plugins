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

import com.github.vlsi.gradle.license.api.DependencyInfo
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SimpleLicense
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SpdxLicenseException
import com.github.vlsi.gradle.license.api.and
import com.github.vlsi.gradle.license.api.or
import com.github.vlsi.gradle.license.api.orLater
import com.github.vlsi.gradle.license.api.with
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader
import java.io.StringWriter

class MetadataStoreTest {
    @Test
    fun spdxExpression() {
        saveMetadata(
            (SpdxLicense.Apache_2_0 or SpdxLicense.MIT) and
                    (SpdxLicense.GPL_2_0_only.orLater() with SpdxLicenseException.Classpath_exception_2_0),
            """
                <license-list version='1'>
                  <components>
                    <component id='org.test:test:1.0.0' licenseFiles='def'>
                      <license-expression>
                        <expression providerId='SPDX'>(Apache-2.0 OR MIT) AND GPL-2.0-only+ WITH Classpath-exception-2.0</expression>
                      </license-expression>
                    </component>
                  </components>
                </license-list>
            """.trimIndent()
        )
    }

    @Test
    fun nonStandardLicense() {
        saveMetadata(
            (SpdxLicense.Apache_2_0 or SimpleLicense("WTFYWPL")) and
                    (SpdxLicense.GPL_2_0_only.orLater() with SpdxLicenseException.Classpath_exception_2_0),
            """
                <license-list version='1'>
                  <components>
                    <component id='org.test:test:1.0.0' licenseFiles='def'>
                      <license-expression>
                        <and>
                          <or>
                            <license providerId='SPDX' id='Apache-2.0' />
                            <license name='WTFYWPL' uri='' />
                          </or>
                          <expression providerId='SPDX'>GPL-2.0-only+ WITH Classpath-exception-2.0</expression>
                        </and>
                      </license-expression>
                    </component>
                  </components>
                </license-list>
            """.trimIndent()
        )
    }

    private fun saveMetadata(
        licenseExpression: LicenseExpression,
        expected: String
    ) {
        val sw = StringWriter()
        val root = File("test")
        val metadata = mapOf(
            moduleComponentId("org.test", "test", "1.0.0") to
                    LicenseInfo(
                        license = licenseExpression,
                        file = null,
                        licenseFiles = File(root, "def")
                    )
        )
        MetadataStore.save(sw, root, DependencyInfo(metadata))
        val res = sw.toString()
        Assertions.assertEquals(
            expected, res
        )

        val parsed = MetadataStore.load(res.byteInputStream(), root)
        Assertions.assertEquals(metadata.toMutableMap(), parsed.dependencies.toMutableMap())
    }

}
