/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.vlsi.gradle.license

import com.github.vlsi.gradle.license.api.SimpleLicense
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.asExpression
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI

class GuessBasedNormalizerTest {
    private val logger = LoggerFactory.getLogger(GuessBasedNormalizer::class.java)

    @Test
    internal fun predictBsd2Clause() {
        val n = GuessBasedNormalizer(logger)
        for (v in SpdxLicense.values()) {
            val normalized = n.normalize(SimpleLicense(v.title))
            Assertions.assertEquals(v.asExpression(), normalized) { "normalize(${v.title})" }
        }
    }

    @Test
    internal fun al11() {
        val n = GuessBasedNormalizer(logger)
        println(n.normalize(SimpleLicense("The Apache Software License, Version 1.1",
            URI("http://www.apache.org/licenses/LICENSE-1.1.txt"))))
    }
}
