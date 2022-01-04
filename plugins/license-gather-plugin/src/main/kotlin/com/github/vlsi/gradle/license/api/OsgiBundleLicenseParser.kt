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

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException

class OsgiBundleLicenseParser(
    private val licenseExpressionParser: LicenseExpressionParser,
    private val lookupLicenseByUri: (URI) -> LicenseExpression?
) {
    private val logger = LoggerFactory.getLogger(OsgiBundleLicenseParser::class.java)

    fun parseOrNull(bundleLicense: String, context: Any): LicenseExpression? {
        return if (bundleLicense.contains(',')) {
            logger.info(
                "Ignoring Bundle-License '{}' in {} since it contains multiple license references",
                bundleLicense,
                context
            )
            null
        } else if (bundleLicense.startsWith("http")) {
            // Infer license from the URI
            val uri = try {
                URI(bundleLicense)
            } catch (e: URISyntaxException) {
                logger.info(
                    "Invalid URI for license in Bundle-License value '{}' in {}",
                    bundleLicense,
                    context,
                    e
                )
                return null
            }
            lookupLicenseByUri(uri)
        } else {
            // Infer license from "SPDX-Expression; licenseURI"
            // We ignore URI as the expression should be more important
            bundleLicense.substringBefore(";").let {
                try {
                    licenseExpressionParser.parse(it)
                } catch (e: ParseException) {
                    logger.info(
                        "Unable to parse Bundle-License value '{}' in {}",
                        bundleLicense,
                        context,
                        e
                    )
                    null
                }
            }
        }
    }
}
