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

package com.github.vlsi.gradle.license.api

import com.github.vlsi.gradle.license.api.SpdxLicense.*

object SpdxLicenseEquivalence {
    val map: Map<LicenseExpression, Set<LicenseExpression>> =
        licenseVersions(AFL_1_1, AFL_1_2, AFL_2_0, AFL_2_1, AFL_3_0)
            .plus(SpdxLicense.values()
                .map { it.orLater() to setOf(it.asExpression()) })
            .plus(licenseVersions(AFL_1_1, AFL_1_2, AFL_2_0, AFL_2_1, AFL_3_0))
            .plusOrLater(AGPL_1_0_or_later)
            .plusOrLater(AGPL_3_0_or_later)
            .plus(licenseVersions(AGPL_1_0_only, AGPL_3_0_only))
            .plus(licenseVersions(AGPL_1_0_only, AGPL_3_0_only))
            .plus(licenseVersions(APSL_1_0, APSL_1_1, APSL_1_2, APSL_2_0))
            .plus(licenseVersions(Apache_1_0, Apache_1_1, Apache_2_0))
            .plus(licenseVersions(Artistic_1_0, Artistic_2_0))
            .plus(licenseVersions(BitTorrent_1_0, BitTorrent_1_1))
            .plus(licenseVersions(CC_BY_1_0, CC_BY_2_0, CC_BY_2_5, CC_BY_3_0, CC_BY_4_0))
            .plus(
                licenseVersions(
                    CC_BY_NC_1_0,
                    CC_BY_NC_2_0,
                    CC_BY_NC_2_5,
                    CC_BY_NC_3_0,
                    CC_BY_NC_4_0
                )
            )
            .plus(
                licenseVersions(
                    CC_BY_NC_ND_1_0,
                    CC_BY_NC_ND_2_0,
                    CC_BY_NC_ND_2_5,
                    CC_BY_NC_ND_3_0,
                    CC_BY_NC_ND_4_0
                )
            )
            .plus(
                licenseVersions(
                    CC_BY_NC_SA_1_0,
                    CC_BY_NC_SA_2_0,
                    CC_BY_NC_SA_2_5,
                    CC_BY_NC_SA_3_0,
                    CC_BY_NC_SA_4_0
                )
            )
            .plus(
                licenseVersions(
                    CC_BY_ND_1_0,
                    CC_BY_ND_2_0,
                    CC_BY_ND_2_5,
                    CC_BY_ND_3_0,
                    CC_BY_ND_4_0
                )
            )
            .plus(
                licenseVersions(
                    CC_BY_SA_1_0,
                    CC_BY_SA_2_0,
                    CC_BY_SA_2_5,
                    CC_BY_SA_3_0,
                    CC_BY_SA_4_0
                )
            )
            .plus(licenseVersions(CDDL_1_0, CDDL_1_1))
            .plus(licenseVersions(CECILL_1_0, CECILL_1_1, CECILL_2_0, CECILL_2_1))
            .plus(licenseVersions(CERN_OHL_1_1, CERN_OHL_1_2))
            .plus(licenseVersions(ECL_1_0, ECL_2_0))
            .plus(licenseVersions(EFL_1_0, EFL_2_0))
            .plus(licenseVersions(EPL_1_0, EPL_2_0))
            .plus(licenseVersions(EUPL_1_0, EUPL_1_1, EUPL_1_2))
            .plusOrLater(GFDL_1_1_or_later)
            .plusOrLater(GFDL_1_2_or_later)
            .plusOrLater(GFDL_1_3_or_later)
            .plus(licenseVersions(GFDL_1_1_only, GFDL_1_2_only, GFDL_1_3_only))
            .plusOrLater(GPL_1_0_or_later)
            .plusOrLater(GPL_2_0_or_later)
            .plusOrLater(GPL_3_0_or_later)
            .plus(licenseVersions(GPL_1_0_only, GPL_2_0_only, GPL_3_0_only))
            .plus(licenseVersions(LAL_1_2, LAL_1_3))
            .plus(licenseVersions(LAL_1_2, LAL_1_3))
            .plusOrLater(LGPL_2_0_or_later)
            .plusOrLater(LGPL_2_1_or_later)
            .plusOrLater(LGPL_3_0_or_later)
            .plus(licenseVersions(LGPL_2_0_only, LGPL_2_1_only, LGPL_3_0_only))
            .plus(licenseVersions(LPL_1_0, LPL_1_02))
            .plus(licenseVersions(LPPL_1_0, LPPL_1_1, LPPL_1_2))
            .plus(licenseVersions(MPL_1_0, MPL_1_1, MPL_2_0))
            .plus(licenseVersions(NPL_1_0, NPL_1_1))
            .plus(licenseVersions(OFL_1_0, OFL_1_1))
            .plus(licenseVersions(OGL_UK_1_0, OGL_UK_2_0, OGL_UK_3_0))
            .plus(
                licenseVersions(
                    OLDAP_1_1,
                    OLDAP_1_2,
                    OLDAP_1_3,
                    OLDAP_1_4,
                    OLDAP_2_0,
                    OLDAP_2_0_1,
                    OLDAP_2_1,
                    OLDAP_2_2,
                    OLDAP_2_2_1,
                    OLDAP_2_2_2,
                    OLDAP_2_3,
                    OLDAP_2_4,
                    OLDAP_2_5,
                    OLDAP_2_6,
                    OLDAP_2_7,
                    OLDAP_2_8
                )
            )
            .plus(licenseVersions(OSL_1_0, OSL_1_1, OSL_2_0, OSL_2_1, OSL_3_0))
            .plus(licenseVersions(PHP_3_0, PHP_3_01))
            .plus(licenseVersions(RPL_1_1, RPL_1_5))
            .plus(licenseVersions(SGI_B_1_0, SGI_B_1_1, SGI_B_2_0))
            .plus(licenseVersions(SISSL, SISSL_1_2))
            .plus(licenseVersions(SISSL, SISSL_1_2))
            .plus(licenseVersions(Spencer_86, Spencer_94, Spencer_99))
            .plus(licenseVersions(TU_Berlin_1_0, TU_Berlin_2_0))
            .plus(licenseVersions(Unicode_DFS_2015, Unicode_DFS_2016))
            .plus(licenseVersions(Unicode_DFS_2015, Unicode_DFS_2016))
            .plus(licenseVersions(W3C_19980720, W3C_20150513)) // should W3C be here as well?
            .plus(licenseVersions(YPL_1_0, YPL_1_1))
            .plus(licenseVersions(ZPL_1_1, ZPL_2_0, ZPL_2_1))
            .plus(licenseVersions(Zimbra_1_3, Zimbra_1_4))
            .plus(licenseVersions(bzip2_1_0_5, bzip2_1_0_6))
            .plus(licenseVersions(copyleft_next_0_3_0, copyleft_next_0_3_1))
            // Accumulate values when same key appears multiple times
            .groupingBy { it.first }
            .foldTo(mutableMapOf(),
                initialValueSelector = { _, _ -> mutableSetOf() },
                operation = { _, acc, e -> acc.apply { acc.addAll(e.second) } })
}

private fun Sequence<Pair<LicenseExpression, Set<LicenseExpression>>>.plusLicense(
    license: License,
    equivalence: LicenseExpression
) =
    plusElement(license.asExpression() to setOf(equivalence))

private fun Sequence<Pair<LicenseExpression, Set<LicenseExpression>>>.plusOrLater(license: SpdxLicense) =
    plusElement(license.asExpression() to setOf(valueOf(license.name.removeSuffix("_or_later") + "_only").orLater()))

fun licenseVersions(vararg licenses: License) =
    licenses
        .asSequence()
        .windowed(size = 2, partialWindows = true) { w ->
            if (w.size == 2) {
                w[0].orLater() to setOf(w[0].asExpression(), w[1].orLater())
            } else { // The latest version
                w[0].orLater() to setOf(w[0].asExpression())
            }
        }
