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

import com.github.vlsi.gradle.appendPlatformLine
import org.eclipse.jgit.attributes.Attribute
import org.eclipse.jgit.attributes.Attributes
import org.eclipse.jgit.attributes.AttributesHandler
import org.eclipse.jgit.attributes.AttributesNode
import org.eclipse.jgit.attributes.AttributesRule
import org.eclipse.jgit.ignore.FastIgnoreRule
import org.eclipse.jgit.ignore.IgnoreNode
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.treewalk.TreeWalk
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.specs.Spec
import java.io.File
import java.nio.file.Path
import java.util.*

open class ParentAndPrefix<T>(
    val ref: T,
    val prefix: String
)

abstract class GitNode<E : GitNode<E>>(val parent: ParentAndPrefix<E>?) {
    val totalPrefixLength: Int =
        parent?.let { it.prefix.length + it.ref.totalPrefixLength + 1 } ?: 0

    protected fun appendPrefix(sb: StringBuilder) {
        if (parent == null) {
            sb.append("/")
            return
        }
        parent.ref.appendPrefix(sb)
        sb.append(parent.prefix)
    }
    protected fun appendPattern(sb: StringBuilder, pattern: String) {
        if (pattern.startsWith('/') && sb.endsWith('/')) {
            sb.setLength(sb.length - 1)
        }
        sb.append(pattern)
    }
}

class DirectorySet<E> {
    private val files = TreeMap<String, E>()

    fun add(prefix: String, action: (ParentAndPrefix<E>?) -> E) {
        val entry = findEntry(prefix)
        files[prefix] =
            action(entry?.let { ParentAndPrefix(entry.value, prefix.substring(entry.key.length)) })
    }

    operator fun get(prefix: String) = findEntry(prefix)?.value

    private fun findEntry(prefix: String): Map.Entry<String, E>? {
        val entry = files.floorEntry(prefix) ?: return null
        val commonPrefix = prefix.commonPrefixWith(entry.key)
        return when {
            commonPrefix.length == entry.key.length -> entry
            else -> findEntry(commonPrefix)
        }
    }

    override fun toString() =
        StringBuilder().also {
            for ((path, value) in files.entries) {
                if (it.isNotEmpty()) {
                    it.appendPlatformLine()
                }
                it.append("# ").appendPlatformLine(path)
                it.append(value)
            }
        }.toString()
}

abstract class GitHolder<T, V>(rootPath: Path) {
    private val rootDir = rootPath.toFile()
    private val rootAbsolutePath = rootDir.canonicalPath

    private val root = DirectorySet<T>()

    protected fun add(prefix: String, init: (ParentAndPrefix<T>?) -> T) {
        root.add(prefix, init)
    }

    protected fun findNode(entryPath: String): T? = root[entryPath]

    abstract fun compute(entryPath: String?, isFile: Boolean): V

    fun compute(element: Any): V =
        when (element) {
            is FileTreeElement -> compute(element)
            is File -> compute(element)
            is RelativePath -> compute(element)
            else -> TODO("Unsupported value: $element")
        }

    @Suppress("MemberVisibilityCanBePrivate")
    fun compute(path: RelativePath): V =
        compute(path.segments.joinToString("/", "/", ""), path.isFile)

    @Suppress("MemberVisibilityCanBePrivate")
    fun compute(element: FileTreeElement): V =
        computeFile(element.file, element.isDirectory)

    @Suppress("MemberVisibilityCanBePrivate")
    fun compute(element: File): V =
        computeFile(element, element.isDirectory)

    private fun computeFile(file: File, isDirectory: Boolean): V {
        val absolutePath = file.canonicalPath
        if (!absolutePath.startsWith(rootAbsolutePath)) {
            return compute(null, false)
        }
        val rel = if (absolutePath.length == rootAbsolutePath.length) {
            "/"
        } else {
            absolutePath.substring(rootAbsolutePath.length).replace('\\', '/')
        }
        return compute(rel, !isDirectory)
    }

    override fun toString() = root.toString()
}

class GitIgnoreFilter(rootPath: Path) : GitHolder<GitIgnoreNode, Boolean>(rootPath), Spec<Any> {
    override fun isSatisfiedBy(element: Any): Boolean {
        val x = compute(element)
        return x
    }

    fun add(entryPath: String, node: IgnoreNode) {
        add(entryPath) { parent -> GitIgnoreNode(parent, node) }
    }

    override fun compute(entryPath: String?, isFile: Boolean): Boolean =
        when (entryPath) {
            null -> false
            else -> findNode(entryPath)?.run {
                isIgnored(entryPath.substring(totalPrefixLength), isFile)
            } ?: false
        }
}

class GitIgnoreNode(
    parent: ParentAndPrefix<GitIgnoreNode>?,
    private val ignoreNode: IgnoreNode
) : GitNode<GitIgnoreNode>(parent) {
    private fun IgnoreNode.check(entryPath: String, isFile: Boolean): Boolean? {
        // Parse rules in the reverse order that they were read because later
        // rules have higher priority
        for (i in rules.lastIndex downTo 0) {
            val rule = rules[i]
            if (rule.isMatch(entryPath, !isFile, false)) {
                // True when ignored
                return rule.result
            }
        }
        // Not known
        return null
    }

    fun isIgnored(entryPath: String, isFile: Boolean): Boolean =
        ignoreNode.check(entryPath, isFile)
            ?: parent?.let { it.ref.isIgnored(it.prefix + entryPath, isFile) }
            ?: false

    private fun StringBuilder.appendIgnoreRule(rule: FastIgnoreRule) {
        var offs = 0
        if (rule.negation) {
            offs += 1
            append('!')
        }
        val ruleString = rule.toString()
        appendPrefix(this)
        if (ruleString[if (rule.negation) 1 else 0] == '/') {
            offs += 1
        } else {
            append("**/")
        }
        append(ruleString, offs, ruleString.length)
    }

    private fun appendNode(sb: StringBuilder) {
        if (sb.isNotEmpty()) {
            sb.appendPlatformLine()
        }
        for (rule in ignoreNode.rules) {
            sb.appendIgnoreRule(rule)
            sb.appendPlatformLine()
        }
    }

    override fun toString() =
        StringBuilder().also {
            appendNode(it)
        }.toString()
}

object AttributesHandlerPublicApi : AttributesHandler(TreeWalk(null as ObjectReader?)) {
    public override fun expandMacro(attr: Attribute?, result: Attributes?) {
        super.expandMacro(attr, result)
    }
}

class GitAttributesNode(
    parent: ParentAndPrefix<GitAttributesNode>?,
    private val attributesNode: AttributesNode
) : GitNode<GitAttributesNode>(parent) {
    fun mergeTo(result: Attributes, entryPath: String?, isFile: Boolean) {
        if (entryPath == null) {
            return
        }

        // Parse rules in the reverse order that they were read since the last
        // entry should be used
        for (rule in attributesNode.rules.asReversed()) {
            if (rule.isMatch(entryPath, !isFile)) {
                // Parses the attributes in the reverse order that they were
                // read since the last entry should be used
                for (attr in rule.attributes.asReversed()) {
                    AttributesHandlerPublicApi.expandMacro(attr, result)
                }
            }
        }

        // Merge parent attributes
        parent?.let { it.ref.mergeTo(result, it.prefix + entryPath, isFile) }
    }

    private fun StringBuilder.appendAttributesRule(rule: AttributesRule) {
        appendPrefix(this)
        if (!rule.pattern.startsWith("/")) {
            append("**/")
        }
        appendPattern(this, rule.toString())
    }

    private fun appendNode(sb: StringBuilder) {
        if (sb.isNotEmpty()) {
            sb.appendPlatformLine()
        }
        for (rule in attributesNode.rules) {
            sb.appendAttributesRule(rule)
            sb.appendPlatformLine()
        }
    }

    override fun toString() =
        StringBuilder().also {
            appendNode(it)
        }.toString()
}

class GitAttributesMerger(rootPath: Path) : GitHolder<GitAttributesNode, Attributes>(rootPath) {
    operator fun set(entryPath: String, node: AttributesNode) {
        add(entryPath) { parent -> GitAttributesNode(parent, node) }
    }

    override fun compute(entryPath: String?, isFile: Boolean) =
        Attributes().also {
            if (entryPath != null) {
                findNode(entryPath)?.run {
                    mergeTo(it, entryPath.substring(totalPrefixLength), isFile)
                }
                // Remove unspecified attributes
                for (attribute in it.all) {
                    if (attribute.state == Attribute.State.UNSPECIFIED) {
                        it.remove(attribute.key)
                    }
                }
            }
        }
}
