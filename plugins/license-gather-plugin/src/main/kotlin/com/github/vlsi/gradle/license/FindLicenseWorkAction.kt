/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.vlsi.gradle.license

import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

@Suppress("UnstableApiUsage")
interface FindLicenseParameters : WorkParameters {
    val id : Property<String>
    val file : Property<File>
    val outputDir : Property<File>
}

@Suppress("UnstableApiUsage")
abstract class FindLicenseWorkAction : WorkAction<FindLicenseParameters> {
    override fun execute() {
        FindLicense(parameters.id.get(), parameters.file.get(), parameters.outputDir.get()).run()
    }
}
