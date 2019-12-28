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
package com.github.vlsi.gradle.buildtools

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.file.CopySpec
import org.gradle.kotlin.dsl.filter

/**
 * We can't use [crlf] plugin while developing it, so it is a small shim.
 */
fun CopySpec.filterEolSimple(eol: String) {
    filteringCharset = "UTF-8"
    filter(
        FixCrLfFilter::class, mapOf(
            "eol" to FixCrLfFilter.CrLf.newInstance(eol),
            "fixlast" to true,
            "ctrlz" to FixCrLfFilter.AddAsisRemove.newInstance("asis")
        )
    )
}
