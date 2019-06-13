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

import groovy.util.XmlSlurper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader

class PomParsingTest {
    @Test
    internal fun basicInfo() {
        val xml = """
            <project>
            <parent>
              <groupId>org.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0.0</version>
            </parent>
            <groupId>org.test</groupId>
            <artifactId>test</artifactId>
            <version>2.0.0</version>
            <licenses>
              <license>
                <name>licensename</name>
                <url>licenseurl</url>
              </license>
            </licenses>
            </project>
        """.trimIndent()
        val parsedPom = XmlSlurper().parse(StringReader(xml)).parsePom()

        Assertions.assertEquals(
            "PomContents(parentId=org.example:parent:1.0.0," +
                    " id=org.test:test:2.0.0," +
                    " licenses=[SimpleLicense(title=licensename, uri=[licenseurl])])",
            parsedPom.toString()
        )
    }

    @Test
    internal fun twoLicenses() {
        val xml = """
            <project>
            <parent>
              <groupId>org.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0.0</version>
            </parent>
            <groupId>org.test</groupId>
            <artifactId>test</artifactId>
            <version>2.0.0</version>
            <licenses>
              <license>
                <name>Apache-2.0</name>
                <url>licenseurl</url>
              </license>
              <license>
                <name>Apache-2.0</name>
                <url>licenseurl</url>
              </license>
            </licenses>
            </project>
        """.trimIndent()
        val parsedPom = XmlSlurper().parse(StringReader(xml)).parsePom()

        // License is not normalized to SPDX at parse step
        Assertions.assertEquals(
            "PomContents(parentId=org.example:parent:1.0.0," +
                    " id=org.test:test:2.0.0," +
                    " licenses=[SimpleLicense(title=Apache-2.0, uri=[licenseurl]), SimpleLicense(title=Apache-2.0, uri=[licenseurl])])",
            parsedPom.toString()
        )
    }

    @Test
    internal fun groupInherits() {
        val xml = """
            <project>
            <parent>
              <groupId>org.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0.0</version>
            </parent>
            <artifactId>test</artifactId>
            <version>2.0.0</version>
            </project>
        """.trimIndent()
        val parsedPom = XmlSlurper().parse(StringReader(xml)).parsePom()

        Assertions.assertEquals(
            "PomContents(parentId=org.example:parent:1.0.0," +
                    " id=org.example:test:2.0.0, licenses=[])",
            parsedPom.toString()
        )
    }

    @Test
    internal fun parentMissing() {
        val xml = """
            <project>
            <groupId>org.test</groupId>
            <artifactId>test</artifactId>
            <version>2.0.0</version>
            </project>
        """.trimIndent()
        val parsedPom = XmlSlurper().parse(StringReader(xml)).parsePom()

        Assertions.assertEquals(
            "PomContents(parentId=null, id=org.test:test:2.0.0, licenses=[])",
            parsedPom.toString()
        )
    }
}
