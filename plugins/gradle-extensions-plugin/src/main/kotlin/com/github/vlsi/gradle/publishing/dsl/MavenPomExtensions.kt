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
package com.github.vlsi.gradle.publishing.dsl

import org.gradle.api.publish.maven.MavenPom

/**
 * Removes `<scope>compile</scope>` and removes `<dependencyManagement>` to make pom.xml smaller.
 */
fun MavenPom.simplifyXml() = withXml {
    val sb = asString()
    var s = sb.toString()
    // <scope>compile</scope> is Maven default, so delete it
    s = s.replace("<scope>compile</scope>", "")
    // Cut <dependencyManagement> because all dependencies have the resolved versions
    s = s.replace(
        Regex(
            "<dependencyManagement>.*?</dependencyManagement>",
            RegexOption.DOT_MATCHES_ALL
        ),
        ""
    )
    sb.setLength(0)
    sb.append(s)
    // Re-format the XML
    asNode()
}
