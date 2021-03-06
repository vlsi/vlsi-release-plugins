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
package com.github.vlsi.gradle.release

import java.net.URI

interface GitUrlConventions {
    val pushUrl: String
    val pagesUri: URI get() = URI(pushUrl)
    fun tagUri(tag: String): URI
}

class GitHub(val organization: String, val repo: String) : GitUrlConventions {
    override val pushUrl: String
        get() = "https://github.com/$organization/$repo.git"

    override val pagesUri: URI
        get() = URI("https://$organization.github.io/$repo")

    override fun tagUri(tag: String) = URI("https://github.com/$organization/$repo/tree/$tag")
}

class GitBox(val repo: String) : GitUrlConventions {
    override val pushUrl: String
        get() = "https://gitbox.apache.org/repos/asf/$repo.git"

    override fun tagUri(tag: String) = URI("https://gitbox.apache.org/repos/asf?p=$repo.git;a=tag;h=refs/tags/$tag")
}

class GitDaemon(val host: String, val repo: String) : GitUrlConventions {
    override val pushUrl: String
        get() = "git://$host/$repo.git"

    override val pagesUri: URI
        get() = URI("http://$host:8888")

    override fun tagUri(tag: String) = URI("$pushUrl/tags/$tag")
}
