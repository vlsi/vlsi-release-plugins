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
package com.github.vlsi.gradle.crlf

import com.github.vlsi.gradle.git.FilterAutoCrlf
import com.github.vlsi.gradle.git.FilterAutoLf
import com.github.vlsi.gradle.git.FilterTextCrlf
import com.github.vlsi.gradle.git.FilterTextLf
import com.github.vlsi.gradle.git.FindGitAttributes
import com.github.vlsi.gradle.git.GitProperties
import org.eclipse.jgit.attributes.Attributes
import org.eclipse.jgit.lib.CoreConfig
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.* // ktlint-disable
import java.nio.charset.StandardCharsets

class CrLfSpec(val textEol: LineEndings = LineEndings.SYSTEM) {
    fun CopySpec.textFrom(o: Any, eol: LineEndings = textEol) =
        from(o) {
            filter(eol)
        }

    fun CopySpec.textFrom(o: Any, action: AutoClassifySpec.() -> Unit) =
        from(o, textEol) {
            action()
        }

    fun CopySpec.gitattributes(props: GitProperties) =
        applyFilter(gitattributes(textEol, props))

    fun CopySpec.gitattributes(props: Provider<GitProperties>) =
        applyFilter(gitattributes(textEol, props))

    fun CopySpec.gitattributes(task: TaskProvider<FindGitAttributes>) {
        from(task)
        applyFilter(gitattributes(textEol, task))
    }
}

private fun gitattributes(textEol: LineEndings, props: GitProperties): Action<in FileCopyDetails> =
    Action { applyFilter(props, textEol) }

private fun gitattributes(textEol: LineEndings, props: Provider<GitProperties>): Action<in FileCopyDetails> =
    Action { applyFilter(props.get(), textEol) }

private fun gitattributes(textEol: LineEndings, task: TaskProvider<FindGitAttributes>): Action<in FileCopyDetails> =
    Action { applyFilter(task.get().props, textEol) }

private fun CopySpec.applyFilter(action: Action<in FileCopyDetails>) {
    filteringCharset = StandardCharsets.ISO_8859_1.name()
    eachFile(action)
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

private fun LineEndings.toStreamType(attributes: Attributes): CoreConfig.EolStreamType {
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
