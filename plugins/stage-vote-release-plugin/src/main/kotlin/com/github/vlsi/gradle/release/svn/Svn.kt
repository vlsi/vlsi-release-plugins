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
package com.github.vlsi.gradle.release.svn

import com.github.vlsi.gradle.license.attr
import com.github.vlsi.gradle.license.get
import com.github.vlsi.gradle.license.getList
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.OffsetDateTime
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.File

class Svn(val execOperations: ExecOperations, val logger: Logger, val projectDir: File, val uri: URI) : SvnCredentials {
    override var username: String? = null
    override var password: String? = null

    // <?xml version="1.0" encoding="UTF-8"?>
    // <lists>
    //   <list path="https://dist.apache.org/repos/dist/dev/jmeter">
    //     <entry kind="dir">
    //       <name>apache-jmeter-5.2-rc1</name>
    //       <commit revision="36237">
    //         <author>milamber</author>
    //         <date>2019-10-07T21:14:46.782779Z</date>
    //       </commit>
    //     </entry>
    // ...
    private fun GPathResult.toSvnEntry(path: String) = SvnEntry(
        kind = attr("kind").let { EntryKind.valueOf(it.uppercase()) },
        path = path,
        name = get("name").text(),
        size = get("size").text().ifBlank { null }?.toLong(),
        commit = get("commit").toCommit()
    )

    private fun GPathResult.toCommit() = SvnCommit(
        revision = attr("revision").toInt(),
        author = get("author").text(),
        date = OffsetDateTime.parse(get("date").text())
    )

    fun cat(options: CatOptions.() -> Unit): ByteArray {
        val opts = CatOptions().also {
            it.credentialsFrom(this)
        }.also(options)

        val file = opts.file

        val revisionSuffix = opts.revision?.let { "@$it" } ?: ""
        logger.lifecycle("Fetching {}/{}{}", uri, file, revisionSuffix)

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir = projectDir
            commandLine("svn", "cat")
            opts.username?.let { args("--username", it) }
            opts.password?.let { args("--password", it) }
            opts.revision?.let { args("--revision", it) }
            args("$uri/$file")
            isIgnoreExitValue = true
            standardOutput = stdout
            errorOutput = stderr
        }

        if (result.exitValue != 0) {
            throw GradleException("Unable to fetch $uri/$file, revision ${opts.revision}: $stderr")
        }
        return stdout.toByteArray()
    }

    fun ls(options: LsOptions.() -> Unit): List<SvnEntry> {
        val opts = LsOptions().also {
            it.credentialsFrom(this)
        }.also(options)

        val revisionSuffix = opts.revision?.let { "@$it" } ?: ""
        val contents = if (opts.folders.isNotEmpty()) {
            "folders ${opts.folders}"
        } else {
            "contents"
        }
        logger.lifecycle("Listing SVN {} at {}{}", contents, uri, revisionSuffix)

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir = projectDir
            commandLine("svn", "ls", "--xml", "--depth", opts.depth.name.lowercase())
            for (folder in opts.folders) {
                args("$uri/$folder/")
            }
            opts.username?.let { args("--username", it) }
            opts.password?.let { args("--password", it) }
            isIgnoreExitValue = true
            standardOutput = stdout
            errorOutput = stderr
        }

        if (result.exitValue != 0) {
            throw GradleException("Unable to list folders ${opts.folders} at $uri: $stderr")
        }

        val xml = XmlSlurper().parse(stdout.toByteArray().inputStream())
        return xml.getList("list")
            .flatMap {
                val path = it.attr("path")
                it.getList("entry").map { entry ->
                    entry.toSvnEntry(path)
                }
            }
    }
}

class LsOptions : SvnCredentials {
    override var username: String? = null
    override var password: String? = null
    val folders = mutableListOf<String>()
    var depth = LsDepth.IMMEDIATES
    val revision: Int? = null
}

class CatOptions : SvnCredentials {
    override var username: String? = null
    override var password: String? = null
    lateinit var file: String
    var revision: Int? = null
}

interface SvnCredentials {
    var username: String?
    var password: String?
    fun credentialsFrom(other: SvnCredentials) {
        username = other.username
        password = other.password
    }
}

data class SvnList(
    val path: String,
    val entries: List<SvnEntry>
)

data class SvnEntry(
    val kind: EntryKind,
    val path: String,
    val name: String,
    val size: Long?,
    val commit: SvnCommit
)

data class SvnCommit(
    val revision: Int,
    val author: String,
    val date: OffsetDateTime
)

enum class EntryKind {
    DIR, FILE
}

enum class LsDepth {
    EMPTY, FILES, IMMEDIATES, INFINITY
}
