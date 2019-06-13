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

import java.util.*

private enum class TokenType {
    LBRACE,
    LITERAL,
    OR,
    AND,
    WITH,
    PLUS,
    RBRACE
}

private data class Token(val type: TokenType, val position: IntRange, val value: String)

class ParseException(
    message: String,
    position: IntRange,
    expression: String,
    cause: Throwable? = null
) : Throwable(
    """
        $message
        input: $expression
               ${" ".repeat(position.first)}^${if (position.first != position.last) "_".repeat(
        position.last - position.first - 1
    ) + "^" else ""}""".trimIndent() +
            " error here",
    cause
)

class LicenseExpressionParser(private val titleParser: LicenseParser = DefaultLicenseParser) {
    companion object {
        private val tokenRegex = Regex("[-A-Za-z0-9_.]+|[()+]")
    }

    private fun String.tokenize() =
        tokenRegex.findAll(this)
            .map {
                Token(
                    position = it.range, value = it.value,
                    type = when (it.value.toUpperCase()) {
                        "(" -> TokenType.LBRACE
                        ")" -> TokenType.RBRACE
                        "+" -> TokenType.PLUS
                        "WITH" -> TokenType.WITH
                        "AND" -> TokenType.AND
                        "OR" -> TokenType.OR
                        else -> TokenType.LITERAL
                    }
                )
            }

    fun parse(value: String): LicenseExpression {
        val operators = ArrayDeque<Token>()
        val rpn = ArrayDeque<Token>()
        for (t in value.tokenize()) {
            when (t.type) {
                TokenType.LITERAL -> rpn.add(t)
                TokenType.LBRACE -> operators.push(t)
                TokenType.RBRACE -> {
                    while (operators.isNotEmpty()) {
                        val op = operators.peek()
                        if (op.type == TokenType.LBRACE) {
                            break
                        }
                        rpn.add(operators.pop())
                    }
                    if (operators.isEmpty()) {
                        throw ParseException("Unmatched closing brace $t", t.position, value)
                    }
                    operators.pop() // ignore LBRACE
                }
                TokenType.PLUS, TokenType.WITH, TokenType.AND, TokenType.OR -> {
                    while (operators.isNotEmpty()) {
                        if (operators.peek().type < t.type) {
                            break
                        }
                        rpn.add(operators.pop())
                    }
                    operators.push(t)
                }
            }
        }
        rpn.addAll(operators)
        val result = ArrayDeque<LicenseExpression>()
        while(rpn.isNotEmpty()) {
            val t = rpn.removeFirst()
            when (t.type) {
                TokenType.LITERAL ->
                    when {
                        t.value == "NONE" -> result.push(LicenseExpression.NONE)
                        t.value == "NOASSERION" -> result.push(LicenseExpression.NOASSERTION)
                        rpn.peekFirst()?.type != TokenType.WITH -> result.push(titleParser.parseLicense(t.value).asExpression())
                        else -> {
                            val withToken = rpn.pop()
                            val license = result.pop()
                            if (license !is SimpleLicenseExpression) {
                                throw ParseException(
                                    "Left argument of 'with exception' must be a SimpleLicenseExpression. Actual argument is ${license::class.simpleName}: [$license]",
                                    withToken.position,
                                    value
                                )
                            }
                            result.push(license with titleParser.parseException(t.value))
                        }
                    }
                TokenType.OR -> {
                    if (result.size < 2) {
                        throw ParseException(
                            "OR expression requires two arguments",
                            t.position,
                            value
                        )
                    }
                    result.push(result.pop() or result.pop())
                }
                TokenType.AND -> {
                    if (result.size < 2) {
                        throw ParseException(
                            "AND expression requires two arguments",
                            t.position,
                            value
                        )
                    }
                    result.push(result.pop() and result.pop())
                }
                TokenType.WITH -> {
                    val exception = result.poll()
                    val license = result.poll()
                    throw ParseException(
                        "'With exception' should be applied to SimpleLicenseExpression and LicenseException. Actual arguments are [$license] and [$exception]",
                        t.position,
                        value
                    )
                }
                TokenType.PLUS -> {
                    val license = result.pop()
                    if (license !is JustLicense) {
                        throw ParseException(
                            "'Or later' modifier can be applied to License only. Actual argument is ${license::class.simpleName}: [$license]",
                            t.position,
                            value
                        )
                    }
                    result.push(license.orLater())
                }
                TokenType.LBRACE -> throw ParseException("Unclosed open brace", t.position, value)
                TokenType.RBRACE -> throw ParseException("Extra closing brace", t.position, value)
            }
        }
        if (result.isEmpty()) {
            throw ParseException("Result is empty", 0..0, value)
        }
        if (result.size > 1) {
            throw ParseException("Multiple expressions in the output. Probably, AND/OR is missing: [$result]", 0..0, value)
        }
        return result.first
    }
}
