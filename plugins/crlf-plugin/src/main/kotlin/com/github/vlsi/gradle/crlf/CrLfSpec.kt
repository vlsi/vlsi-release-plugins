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
import com.github.vlsi.gradle.git.GitAttributesMerger
import com.github.vlsi.gradle.git.GitProperties
import org.eclipse.jgit.attributes.Attributes
import org.eclipse.jgit.lib.CoreConfig
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.filter
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

    /**
     * Recognizes text files automatically and converts them to the appropriate eol-style.
     * Note: due to https://github.com/gradle/gradle/issues/1191, Gradle does not use
     * filtering for up-to-date checks, so you need to add explicit "task.inputs.property"
     * for proper incremental task operation.
     * This is similar to Git's `* text=auto`
     */
    fun CopySpec.textAuto() {
        val streamType = when (textEol) {
            LineEndings.CRLF -> CoreConfig.EolStreamType.AUTO_CRLF
            LineEndings.LF -> CoreConfig.EolStreamType.AUTO_LF
        }
        filterBinary { filterEol(streamType) }
    }

    fun CopySpec.gitattributes(props: GitProperties) {
        if (this is Task) {
            wa1191SetInputs(props.attrs)
        }
        filterBinary { filterEol(props, textEol) }
    }

    fun CopySpec.gitattributes(props: Provider<GitProperties>) {
        if (this is Task) {
            wa1191SetInputs(props)
        }
        filterBinary { filterEol(props.get(), textEol) }
    }

    fun CopySpec.gitattributes(task: TaskProvider<FindGitAttributes>) {
        from(task)
        if (this is Task) {
            wa1191SetInputs(task)
        }
        filterBinary { filterEol(task.get().props, textEol) }
    }

    fun Task.wa1191SetInputs(attributes: GitAttributesMerger) {
        wa1191Internal(attributes.toString())
    }

    fun Task.wa1191SetInputs(props: Provider<GitProperties>) {
        wa1191Internal(props.map { it.attrs.toString() })
    }

    fun Task.wa1191SetInputs(task: TaskProvider<FindGitAttributes>) {
        wa1191Internal(task.map { it.props.attrs.toString() })
    }

    private fun Task.wa1191Internal(attributes: Any) {
        // Workaround https://github.com/gradle/gradle/issues/1191
        // Copy tasks do not consider filter/eachFile/expansion properties in up-to-date checks
        inputs.property("gitproperties", attributes)
    }
}

private fun CopySpec.filterBinary(action: FileCopyDetails.() -> Unit) {
    filteringCharset = StandardCharsets.ISO_8859_1.name()
    if (this is Task) {
        // https://github.com/gradle/gradle/issues/1191
        // Copy tasks do not consider filter/eachFile/expansion properties in up-to-date checks
    }
    eachFile(action)
}

private fun FileCopyDetails.filterEol(props: GitProperties, textEol: LineEndings) {
    val attributes = props.attrs.compute(this)
    if (attributes.isSet("executable")) {
        permissions {
            unix("755")
        }
    }
    val streamType = textEol.toStreamType(attributes)
    filterEol(streamType)
}

private fun FileCopyDetails.filterEol(streamType: CoreConfig.EolStreamType) {
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
