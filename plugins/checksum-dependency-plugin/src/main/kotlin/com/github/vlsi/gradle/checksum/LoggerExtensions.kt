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
package com.github.vlsi.gradle.checksum

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

internal inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) {
        debug(message())
    }
}

internal inline fun Logger.info(message: () -> String) {
    if (isInfoEnabled) {
        info(message())
    }
}

internal inline fun Logger.lifecycle(message: () -> String) {
    if (isLifecycleEnabled) {
        lifecycle(message())
    }
}

internal inline fun Logger.log(level: LogLevel, message: () -> String) {
    if (isEnabled(level)) {
        log(level, message())
    }
}
