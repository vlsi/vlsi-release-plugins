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

import com.github.vlsi.gradle.license.api.DisjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.License
import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionSet
import com.github.vlsi.gradle.license.api.LicenseExpressionSetOperation
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SpdxLicenseException
import com.github.vlsi.gradle.license.api.asExpression
import com.github.vlsi.gradle.license.api.disjunctions
import com.github.vlsi.gradle.license.api.orLater
import com.github.vlsi.gradle.license.api.with

/**
 * See https://apache.org/legal/resolved.html
 */
enum class AsfLicenseCategory : LicenseExpressionSet {
    A, B, X, UNKNOWN;

    override val disjunctions: Set<LicenseExpression>
        get() = when (this) {
            A -> aLicenses
            B -> bLicenses
            X -> xLicenses
            UNKNOWN -> setOf()
        }

    override val conjunctions: Set<LicenseExpression>
        get() = setOf(DisjunctionLicenseExpression(disjunctions))

    companion object {
        @JvmStatic
        fun of(license: License) = of(license.asExpression())

        @JvmStatic
        fun of(expr: LicenseExpression) =
            when (expr) {
                in aLicenses -> A
                in bLicenses -> B
                in xLicenses -> X
                else -> null
            }

        private val equivalence = LicenseEquivalence()

        // For the purposes of being included in an Apache Software Foundation product, the following licenses are considered to be similar in terms to the Apache License 2.0
        private val aLicenses : Set<LicenseExpression> = listOf(
            SpdxLicense.Apache_2_0,
            SpdxLicense.Apache_1_0,
            SpdxLicense.Apache_1_1,
            SpdxLicense.PHP_3_01,
            // MX4J License
            SpdxLicense.BSD_2_Clause,
            SpdxLicense.BSD_2_Clause_FreeBSD,
            SpdxLicense.BSD_2_Clause_NetBSD,
            SpdxLicense.BSD_3_Clause,
            SpdxLicense.BSD_3_Clause_Attribution,
            SpdxLicense.BSD_3_Clause_Clear,
            SpdxLicense.BSD_3_Clause_LBNL,
            SpdxLicense.BSD_3_Clause_No_Nuclear_License,
            SpdxLicense.BSD_3_Clause_No_Nuclear_License_2014,
            SpdxLicense.BSD_3_Clause_No_Nuclear_Warranty,
            SpdxLicense.PostgreSQL,
            SpdxLicense.EPL_1_0,
            SpdxLicense.MIT,
            SpdxLicense.X11,
            SpdxLicense.ISC,
            // Standard ML of New Jersey, Cup Parser Generator
            SpdxLicense.ICU,
            SpdxLicense.NCSA,
            SpdxLicense.W3C,
            SpdxLicense.W3C_19980720,
            SpdxLicense.W3C_20150513,
            // X.Net
            SpdxLicense.zlib_acknowledgement,
            SpdxLicense.libpng_2_0,
            // FSF autoconf license
            // DejaVu Fonts (Bitstream Vera/Arev licenses)
            // Academic Free License 3.0
            // Service+Component+Architecture+Specifications
            // OOXML XSD ECMA License
            SpdxLicense.MS_PL,
            SpdxLicense.CC0_1_0, // Creative Commons Copyright-Only Dedication
            SpdxLicense.Python_2_0,
            SpdxLicense.APAFML,
            SpdxLicense.BSL_1_0,
            // License for CERN packages in COLT but note that this applies only to CERN packages in COLT and not others
            SpdxLicense.OGL_UK_3_0, // It is not clear if 1.0/2.0 are allowed
            SpdxLicense.WTFPL,
            // The Romantic WTF public license: https://github.com/pygy/gosub/blob/master/LICENSE
            SpdxLicense.Unicode_DFS_2015,
            SpdxLicense.Unicode_DFS_2016,
            SpdxLicense.ZPL_2_0,
            // ACE license
            SpdxLicense.UPL_1_0
            // Open Grid Forum License
            // IP Rights Grant
        ).asSequence()
            .map { it.asExpression() }
            .plus(
                equivalence.expand(
                    SpdxLicense.GPL_1_0_or_later with SpdxLicenseException.Classpath_exception_2_0
                ).disjunctions()
            )
            .toSet()

        // Software under the following licenses may be included in binary form within an Apache product if the inclusion is appropriately labeled (see above)
        // By including only the object/binary form, there is less exposed surface area of the third-party work from which a work might be derived; this addresses the second guiding principle of this policy
        private val bLicenses: Set<LicenseExpression> = listOf(
            SpdxLicense.CDDL_1_0,
            SpdxLicense.CDDL_1_1,
            SpdxLicense.CPL_1_0,
            SpdxLicense.EPL_1_0,
            SpdxLicense.IPL_1_0,
            SpdxLicense.MPL_1_0,
            SpdxLicense.MPL_1_1,
            SpdxLicense.MPL_2_0,
            SpdxLicense.SPL_1_0,
            SpdxLicense.OSL_3_0,
            SpdxLicense.ErlPL_1_1,
            // https://www.apache.org/legal/resolved.html#cc-by
            SpdxLicense.CC_BY_2_5,
            SpdxLicense.CC_BY_3_0,
            SpdxLicense.CC_BY_4_0,
            // https://www.apache.org/legal/resolved.html#cc-sa
            SpdxLicense.CC_BY_SA_2_5,
            SpdxLicense.CC_BY_SA_3_0,
            SpdxLicense.CC_BY_SA_4_0,
            // UnRAR License (only for unarchiving)
            SpdxLicense.OFL_1_0,
            SpdxLicense.OFL_1_1,
            // Ubuntu font licence
            SpdxLicense.IPA,
            SpdxLicense.Ruby,
            SpdxLicense.EPL_2_0
        ).mapTo(mutableSetOf()) { it.asExpression() }

        // The following licenses may NOT be included within Apache products
        private val xLicenses : Set<LicenseExpression> = listOf(
            // Binary Code License (BCL)
            // Intel Simplified Software License
            // JSR-275 License
            // Microsoft Limited Public License
            // Amazon Software License (ASL)
            // Java SDK for Satori RTM license
            // Redis Source Available License (RSAL)
            // Booz Allen Public License
            // Sun Community Source License 3.0
            // Special exceptions to the GNU GPL (e.g. GNU Classpath) unless otherwise permitted elsewhere on this page.
            SpdxLicense.QPL_1_0,
            SpdxLicense.Sleepycat,
            // Server Side Public License (SSPL) version 1
            SpdxLicense.CPOL_1_02,
            SpdxLicense.BSD_4_Clause,
            SpdxLicense.BSD_4_Clause,
            SpdxLicense.BSD_4_Clause_UC,
            SpdxLicense.BSD_2_Clause_Patent, // Facebook BSD+Patents license
            SpdxLicense.NPL_1_0,
            SpdxLicense.NPL_1_1,
            // The Solipsistic Eclipse Public License
            // The "Don't Be A Dick" Public License
            SpdxLicense.JSON
        ).asSequence()
            .map { it.asExpression() }
            .plus(listOf(
                // Creative Commons Non-Commercial variants
                SpdxLicense.CC_BY_NC_1_0,
                SpdxLicense.CC_BY_NC_ND_1_0,
                SpdxLicense.CC_BY_NC_SA_1_0,
                // Special exceptions to the GNU GPL (e.g. GNU Classpath) unless otherwise permitted elsewhere on this page.
                SpdxLicense.GPL_1_0_only,
                SpdxLicense.AGPL_3_0_only,
                SpdxLicense.LGPL_2_0_only
            )
                .flatMap { equivalence.expand(it.orLater()).disjunctions() })
            .toSet()
    }
}
