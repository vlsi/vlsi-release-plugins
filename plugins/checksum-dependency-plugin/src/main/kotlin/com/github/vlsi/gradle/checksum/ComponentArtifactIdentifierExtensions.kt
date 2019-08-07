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
package com.github.vlsi.gradle.checksum

import com.github.vlsi.gradle.checksum.model.Id
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier

private fun ComponentArtifactIdentifier.artifactOrSignatureId(artifact: Boolean): Id {
    val id = componentIdentifier as ModuleComponentIdentifier
    var classifier: String? = null
    var extension: String
    if (this is DefaultModuleComponentArtifactIdentifier) {
        classifier = name.classifier
        extension = name.extension ?: DependencyArtifact.DEFAULT_TYPE
    } else {
        extension = DependencyArtifact.DEFAULT_TYPE
    }

    when {
        extension.endsWith(".asc") -> if (artifact) {
            extension = extension.removeSuffix(".asc")
        }
        else -> if (!artifact) {
            extension += ".asc"
        }
    }

    return Id(id.group, id.module, id.version, classifier, extension)
}

internal val ComponentArtifactIdentifier.artifactDependencyId: Id
    get() =
        artifactOrSignatureId(true)

internal val ComponentArtifactIdentifier.signatureDependencyId: Id
    get() =
        artifactOrSignatureId(false)

internal val ComponentArtifactIdentifier.artifactDependency: String
    get() =
        artifactOrSignatureId(true).dependencyNotation

internal val ComponentArtifactIdentifier.signatureDependency: String
    get() =
        artifactOrSignatureId(false).dependencyNotation
