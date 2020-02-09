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
package com.github.vlsi.gradle.git

import org.eclipse.jgit.attributes.AttributesNode
import org.eclipse.jgit.ignore.IgnoreNode
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

private fun <E> MutableList<E>.removeLast() = removeAt(size - 1)

class GitProperties(root: Path) {
    val ignores = GitIgnoreFilter(root)
    val attrs = GitAttributesMerger(root)

    override fun toString(): String {
        return "GitProperties(ignores=$ignores, attrs=$attrs)"
    }
}

fun findGitproperties(root: Path, maxDepth: Int = Int.MAX_VALUE): GitProperties {
    val res = GitProperties(root)
    Files.walkFileTree(
        root,
        EnumSet.noneOf(FileVisitOption::class.java),
        maxDepth,
        GitignoreFinder(res.ignores, res.attrs)
    )
    return res
}

class GitignoreFinder(
    private val ignores: GitIgnoreFilter,
    private val attrs: GitAttributesMerger
) : SimpleFileVisitor<Path>() {
    private val path = mutableListOf<String>()
    private val sb = StringBuilder()
    private val pathSize = mutableListOf<Int>()
    private val rules = mutableListOf<IgnoreNode?>()

    private val entryPath: String
        get() =
            when (path.size) {
                1 -> "/"
                else -> path.subList(1, path.size).joinToString(
                    "/",
                    postfix = "/",
                    prefix = "/"
                )
            }

    private fun checkIgnore(): FileVisitResult {
        if (sb.endsWith("/.git")) {
            return FileVisitResult.SKIP_SUBTREE
        }
        for (i in rules.size - 1 downTo 0) {
            val rule = rules[i] ?: continue
            val dirPath = sb.substring(pathSize[i + 1] + 1)
            when (val match = rule.isIgnored(dirPath, true)) {
                IgnoreNode.MatchResult.NOT_IGNORED -> FileVisitResult.CONTINUE
                IgnoreNode.MatchResult.IGNORED -> return FileVisitResult.SKIP_SUBTREE
                IgnoreNode.MatchResult.CHECK_PARENT -> Unit
                else -> TODO("Unsupported match result $match")
            }
        }
        return FileVisitResult.CONTINUE
    }

    private fun loadGitignore(dir: Path) {
        val gitignore = dir.resolve(".gitignore")
        if (!Files.exists(gitignore)) {
            rules.add(null)
            return
        }
        val rule = Files.newInputStream(gitignore).use {
            IgnoreNode().run {
                parse(it)
                if (rules.isEmpty()) {
                    null
                } else {
                    this
                }
            }
        }
        rule?.let {
            ignores.add(entryPath, it)
        }
        rules.add(rule)
    }

    private fun loadGitattributes(dir: Path) {
        val gitattributes = dir.resolve(".gitattributes")
        if (!Files.exists(gitattributes)) {
            return
        }
        val rule = Files.newInputStream(gitattributes).use {
            AttributesNode().apply { parse(it) }
        }
        if (rule.rules.isNotEmpty()) {
            attrs[entryPath] = rule
        }
    }

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        val dirName = dir.getName(dir.nameCount - 1).toString()

        path.add(dirName)
        pathSize.add(sb.length)
        sb.append('/').append(dirName)
        val action = checkIgnore()
        if (action == FileVisitResult.CONTINUE) {
            loadGitignore(dir)
            loadGitattributes(dir)
        } else {
            rules.add(null)
            // cleanup
            postVisitDirectory(dir, null)
        }
        return action
    }

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        sb.setLength(pathSize.removeLast())
        path.removeLast()
        rules.removeLast()
        return FileVisitResult.CONTINUE
    }
}
