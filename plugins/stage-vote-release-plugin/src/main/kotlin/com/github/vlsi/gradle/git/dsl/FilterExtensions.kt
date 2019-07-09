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
package com.github.vlsi.gradle.git.dsl

import com.github.vlsi.gradle.crlf.CrLfSpec
import com.github.vlsi.gradle.crlf.LineEndings
import com.github.vlsi.gradle.git.FilterAutoCrlf
import com.github.vlsi.gradle.git.FilterAutoLf
import com.github.vlsi.gradle.git.FilterTextCrlf
import com.github.vlsi.gradle.git.FilterTextLf
import com.github.vlsi.gradle.git.FindGitAttributes
import com.github.vlsi.gradle.git.GitProperties
import com.github.vlsi.gradle.git.findGitproperties
import org.eclipse.jgit.attributes.Attributes
import org.eclipse.jgit.lib.CoreConfig
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.* // ktlint-disable
import java.io.File
import java.nio.charset.StandardCharsets

fun PatternFilterable.gitignore(props: GitProperties) {
    val filter = props.ignores
    exclude {
        filter.isSatisfiedBy(it)
    }
}

fun PatternFilterable.gitignore(props: Provider<GitProperties>) =
    exclude {
        props.get().ignores.isSatisfiedBy(it)
    }

fun PatternFilterable.gitignore(task: TaskProvider<FindGitAttributes>) =
    exclude {
        task.get().props.ignores.isSatisfiedBy(it)
    }

fun CopySpec.gitignore(task: TaskProvider<FindGitAttributes>) {
    from(task)
    (this as PatternFilterable).gitignore(task)
}

fun PatternFilterable.gitignore(root: File) {
    val filter by lazy {
        findGitproperties(root.toPath()).ignores
    }
    exclude {
        filter.isSatisfiedBy(it)
    }
}

fun LineEndings.toStreamType(attributes: Attributes): CoreConfig.EolStreamType {
    // EolStreamTypeUtil.detectStreamType is almost a good fit, however
    // it assumes the input comes from-to git repository which is always LF
    if (attributes.isUnset("text")) {
        // "binary" or "-text" => no transformation
        return CoreConfig.EolStreamType.DIRECT
    }

    when (attributes.getValue("eol")) {
        "crlf" -> return CoreConfig.EolStreamType.TEXT_CRLF
        "lf" -> return CoreConfig.EolStreamType.TEXT_LF
    }

    if (attributes.isSet("text")) {
        // File is known to be text, use appropriate EOL
        return when (this) {
            LineEndings.CRLF -> CoreConfig.EolStreamType.TEXT_CRLF
            LineEndings.LF -> CoreConfig.EolStreamType.TEXT_LF
        }
    }

    if (attributes.getValue("text") == "auto") {
        // Autodetect if file is binary or not, and use relevant EOL
        return when (this) {
            LineEndings.CRLF -> CoreConfig.EolStreamType.AUTO_CRLF
            LineEndings.LF -> CoreConfig.EolStreamType.AUTO_LF
        }
    }

    // just in case
    return CoreConfig.EolStreamType.DIRECT
}

private fun FileCopyDetails.applyFilter(props: GitProperties, textEol: LineEndings) {
    val attributes = props.attrs.compute(this)
    if (attributes.isSet("executable")) {
        mode = "755".toInt(8)
    }
    val streamType = textEol.toStreamType(attributes)
    filter(
        when (streamType) {
            CoreConfig.EolStreamType.TEXT_CRLF -> FilterTextCrlf::class
            CoreConfig.EolStreamType.TEXT_LF -> FilterTextLf::class
            CoreConfig.EolStreamType.AUTO_CRLF -> FilterAutoCrlf::class
            CoreConfig.EolStreamType.AUTO_LF -> FilterAutoLf::class
            CoreConfig.EolStreamType.DIRECT -> return
        }
    )
}

fun CrLfSpec.gitattributes(props: GitProperties): Action<in FileCopyDetails> =
    Action { applyFilter(props, textEol) }

fun CrLfSpec.gitattributes(props: Provider<GitProperties>): Action<in FileCopyDetails> =
    Action { applyFilter(props.get(), textEol) }

fun CrLfSpec.gitattributes(task: TaskProvider<FindGitAttributes>): Action<in FileCopyDetails> =
    Action { applyFilter(task.get().props, textEol) }

fun CopySpec.applyFilter(action: Action<in FileCopyDetails>) {
    filteringCharset = StandardCharsets.ISO_8859_1.name()
    eachFile(action)
}

fun CrLfSpec.gitattributes(spec: CopySpec, props: GitProperties) =
    spec.applyFilter(gitattributes(props))

fun CrLfSpec.gitattributes(spec: CopySpec, props: Provider<GitProperties>) =
    spec.applyFilter(gitattributes(props))

fun CrLfSpec.gitattributes(spec: CopySpec, task: TaskProvider<FindGitAttributes>) {
    spec.from(task)
    spec.applyFilter(gitattributes(task))
}
