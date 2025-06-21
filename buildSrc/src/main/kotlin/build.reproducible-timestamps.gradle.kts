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

import org.gradle.api.tasks.bundling.AbstractArchiveTask

tasks.withType<AbstractArchiveTask>().configureEach {
    // Ensure builds are reproducible
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    dirPermissions {
        user {
            read = true
            write = true
            execute = true
        }
        group {
            read = true
            write = true
            execute = true
        }
        other {
            read = true
            execute = true
        }
    }
    filePermissions {
        user {
            read = true
            write = true
        }
        group {
            read = true
            write = true
        }
        other {
            read = true
        }
    }
}
