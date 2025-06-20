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

import com.github.vlsi.gradle.properties.dsl.stringProperty
import com.github.vlsi.gradle.properties.dsl.toBool
import com.github.vlsi.gradle.release.svn.SvnCredentials
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.the
import org.gradle.process.ExecSpec
import org.gradle.work.InputChanges

abstract class SvnmuccTask @Inject constructor() : DefaultTask() {
    @Input
    val repository = project.objects.property<URI>()
        .convention(project.provider {
            project.the<ReleaseExtension>().svnDist.url.get()
        })

    abstract fun operations(inputChanges: InputChanges): List<SvnOperation>
    abstract fun message(): String

    protected fun SvnCredentials.withCredentials() {
        project.the<ReleaseExtension>().svnDist.credentials {
            this@withCredentials.username = username(project)
            this@withCredentials.password = password(project)
        }
    }

    protected fun ExecSpec.svnCredentials() {
        project.the<ReleaseExtension>().svnDist.credentials {
            username(project)?.let { args("--username", it) }
            password(project)?.let { args("--password", it) }
        }
    }

    fun exists(path: String): Boolean {
        val os = ByteArrayOutputStream()
        val absolutePath = "${repository.get()}/$path"
        val result = project.exec {
            workingDir = project.projectDir
            commandLine("svn", "ls", "--depth", "empty", absolutePath)
            svnCredentials()
            isIgnoreExitValue = true
            errorOutput = os
        }
        if (result.exitValue == 0) {
            project.logger.debug("Directory {} exists in SVN", absolutePath)
            return true
        }

        val message = os.toString() // Default encoding is expected
        if (message.contains("E200009")) {
            // E200009: Could not list all targets because some targets don't exist
            project.logger.debug("Directory {} does not exist in SVN", absolutePath)
        } else {
            project.logger.warn("Unable to check existence of {}. Error: {}", absolutePath, message)
        }
        return false
    }

    @Internal
    protected val commandsFile = project.layout.buildDirectory.file("svnmucc/$name.txt")

    @TaskAction
    fun mucc(inputChanges: InputChanges) {
        logger.debug(
            if (inputChanges.isIncremental) "Executing incrementally"
            else "Executing non-incrementally"
        )

        val parentFiles = ParentFilesCollector()
        val muccOps = operations(inputChanges)
        for (o in muccOps) {
            val fileName = when (o) {
                is SvnPut -> o.destination
                is SvnCp -> o.destination
                is SvnMv -> o.destination
                is SvnMkdir -> o.path + "/tmp"
                else -> null
            }
            fileName?.let { parentFiles.add(it) }
        }

        // Create relevant parent directories first, then put files
        // Note all SvnMkdirs are served via parentFiles, so we skip mkdir from muccOps
        val commands = parentFiles.parents
            .asSequence()
            .filterNot { exists(it) }
            .map { SvnMkdir(it) }
            .plus(muccOps.filter { it !is SvnMkdir })
            .map(SvnOperation::toSvn)
            .joinToString("\n")

        val commandsFile = this.commandsFile.get().asFile
        commandsFile.parentFile.mkdir()
        commandsFile.writeText(commands)

        val commitMessage = message()
        if (project.stringProperty("asfDryRun").toBool()) {
            logger.lifecycle(
                "Dry run svnmucc. root={}, message={}, commands:\n{}",
                repository.get(),
                commitMessage,
                commands
            )
            return
        }
        if (commands.isBlank()) {
            logger.lifecycle("Svnmucc skipped")
            return
        }
        logger.lifecycle(
            "Executing svnmucc. root={}, message={}, commands:\n{}",
            repository.get(),
            commitMessage,
            commands
        )
        project.exec {
            workingDir = project.projectDir
            commandLine("svnmucc", "--non-interactive", "--root-url", repository.get())
            svnCredentials()
            args("--extra-args", commandsFile)
            args("--message", commitMessage)
            standardOutput = System.out
        }
    }
}

sealed class SvnOperation {
    abstract fun toSvn(): String
}

data class SvnMkdir(val path: String) : SvnOperation() {
    override fun toSvn() = "mkdir\n$path"
}

data class SvnPut(val file: File, val destination: String) : SvnOperation() {
    override fun toSvn() = "put\n$file\n$destination"
}

data class SvnCp(val revision: Int, val source: String, val destination: String) : SvnOperation() {
    override fun toSvn() = "cp\n$revision\n$source\n$destination"
}

data class SvnMv(val source: String, val destination: String) : SvnOperation() {
    override fun toSvn() = "mv\n$source\n$destination"
}

data class SvnRm(val path: String) : SvnOperation() {
    override fun toSvn() = "rm\n$path"
}
