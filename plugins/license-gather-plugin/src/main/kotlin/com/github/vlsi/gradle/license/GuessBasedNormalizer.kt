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

import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionNormalizer
import com.github.vlsi.gradle.license.api.SimpleLicense
import com.github.vlsi.gradle.license.api.SimpleLicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.WithException
import com.github.vlsi.gradle.license.api.asExpression
import org.slf4j.Logger
import java.net.URI

class GuessBasedNormalizer(
    private val logger: Logger,
    private val similarityThreshold: Double = 42.0
) : LicenseExpressionNormalizer() {

    private val nameGuesser = TfIdfBuilder<LicenseExpression>().apply {
        SpdxLicense.values().forEach { addDocument(it.asExpression(), it.title) }
    }.build()

    private fun String.trimTextExtensions() = removeSuffix(".txt").removeSuffix(".md")
    private fun String.trimWww() =
        when {
            startsWith("//www.") -> "//" + substring(6)
            else -> this
        }

    private fun String.normalizeUri() =
        trimTextExtensions().trimWww()

    private fun URI.looksTheSame(other: URI) =
        schemeSpecificPart == other.schemeSpecificPart ||
                schemeSpecificPart.normalizeUri() == other.schemeSpecificPart.normalizeUri()

    override fun normalize(license: SimpleLicense): LicenseExpression? {
        if (license.title.equals("PUBLIC DOMAIN", ignoreCase = true)) {
            return SpdxLicense.CC0_1_0.asExpression()
        }
        SpdxLicense.fromIdOrNull(license.title)?.let { return it.asExpression() }

        val guessList = nameGuesser.predict(license.title)
            .entries
            .sortedByDescending { it.value }

        val inputUris = license.uri
        if (inputUris.isNotEmpty()) {
            val matchingLicense = guessList
                .asSequence()
                .take(20)
                .firstOrNull { (guess, _) ->
                    when (guess) {
                        is SimpleLicenseExpression -> guess.license
                        is WithException -> guess.license.license
                        else -> null
                    }?.uri?.any { u -> inputUris.any { it.looksTheSame(u) } } ?: false
                }
            if (matchingLicense != null) {
                logger.debug(
                    "Automatically detected license name={} url={} to mean {}",
                    license.title, license.uri, matchingLicense.key
                )
                return matchingLicense.key
            }
        }
        val firstLicense = guessList.first()
        if (firstLicense.value * 100 > similarityThreshold) {
            logger.debug(
                "Automatically detected license {} to mean {}. Other possibilities were {}",
                license.title,
                firstLicense.key,
                guessList.take(10)
            )
            return firstLicense.key
        }
        return null
    }
}
