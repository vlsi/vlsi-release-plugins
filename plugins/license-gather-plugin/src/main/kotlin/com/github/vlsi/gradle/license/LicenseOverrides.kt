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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

class LicenseOverrides {
    private val map = mutableMapOf<String, LicenseOverride>()
    private val usedOverrides = mutableSetOf<String>()

    val unusedOverrides: Set<String> get() = map.keys.minus(usedOverrides)

    fun configurationComplete() {
        usedOverrides.clear()
    }

    operator fun get(id: String): LicenseOverride? = map[id]?.also { usedOverrides.add(id) }

    operator fun get(compId: ModuleComponentIdentifier): LicenseOverride? =
        get(compId.displayName) ?: get("${compId.module}:${compId.version}")
        ?: get("${compId.group}:${compId.module}") ?: get(compId.module)

    operator fun set(id: String, value: LicenseOverride) {
        map[id] = value
    }
}
