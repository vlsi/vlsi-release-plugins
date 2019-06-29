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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.asExpression
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

internal enum class LicenseGroup {
    UNCLEAR,
    ASF_AL2,
    ASF_OTHER,
    AL2,
    OTHER
}

internal val LicenseGroup.title: String
    get() =
        when (this) {
            LicenseGroup.UNCLEAR -> "- Software with unclear license. Please analyze the license and specify manually"
            LicenseGroup.ASF_AL2 -> "- Software produced at the ASF which is available under AL 2.0 (as above)"
            LicenseGroup.ASF_OTHER -> "- Software produced at the ASF which is available under other licenses (not AL 2.0)"
            LicenseGroup.AL2 -> "- Software produced outside the ASF which is available under AL 2.0 (as above)"
            LicenseGroup.OTHER -> "- Software produced outside the ASF which is available under other licenses (not AL 2.0)"
        }

private val asfGroups = setOf(
    "org.codehaus.groovy",
    "oro",
    "xalan",
    "xerces"
)

private fun ModuleComponentIdentifier.looksLikeApache() =
    group.startsWith("org.apache") ||
            group in asfGroups ||
            group == module && group.startsWith("commons-")

internal fun licenseGroupOf(
    id: ModuleComponentIdentifier,
    license: LicenseExpression?
): LicenseGroup = when {
    license == null -> LicenseGroup.UNCLEAR
    id.looksLikeApache() ->
        when (license) {
            SpdxLicense.Apache_2_0.asExpression() -> LicenseGroup.ASF_AL2
            else -> LicenseGroup.ASF_OTHER
        }
    license == SpdxLicense.Apache_2_0.asExpression() -> LicenseGroup.AL2
    else -> LicenseGroup.OTHER
}
