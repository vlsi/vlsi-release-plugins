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

import com.github.vlsi.gradle.buildtools.filterEolSimple

plugins {
    id("java")
    id("build.reproducible-timestamps")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    into("META-INF") {
        filterEolSimple("crlf")
        from("$rootDir/LICENSE")
        from("$rootDir/NOTICE")
    }
    manifest {
        attributes["Bundle-License"] = "Apache-2.0"
        attributes["Specification-Title"] = project.name + " " + project.description
        attributes["Specification-Vendor"] = "Vladimir Sitnikov"
        attributes["Implementation-Vendor"] = "Vladimir Sitnikov"
        attributes["Implementation-Vendor-Id"] = "com.github.vlsi"
        // Implementation-Version is not here to make jar reproducible across versions
    }
}
