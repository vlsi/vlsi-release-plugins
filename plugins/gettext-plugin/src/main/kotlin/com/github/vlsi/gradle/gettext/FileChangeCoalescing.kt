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
package com.github.vlsi.gradle.gettext

import org.gradle.api.file.FileType
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import java.io.File

/**
 * Collapses duplicate [FileChange] events for the same physical file into a single event.
 *
 * Gradle may report the same file as both ADDED and REMOVED in one invocation when the
 * incremental input collection contains multiple entries that resolve to the same path
 * (e.g. overlapping providers from feature variants reusing the main source set).
 * Acting on both events deletes the freshly produced output. See
 * https://github.com/vlsi/vlsi-release-plugins for the original report.
 *
 * Rules applied per `file.absolutePath`:
 *  - non-FILE entries (directories, missing) are dropped;
 *  - any non-REMOVED event (ADDED/MODIFIED) wins over a REMOVED event;
 *  - a pure-REMOVED event is suppressed when the file is still present in
 *    [currentSourceFiles], i.e. the snapshot still contains it.
 *
 * Insertion order is preserved so processing matches the original iteration order.
 */
internal fun coalesceFileChanges(
    changes: Iterable<FileChange>,
    currentSourceFiles: Set<File>,
): List<FileChange> {
    val byPath = LinkedHashMap<String, FileChange>()
    for (change in changes) {
        if (change.fileType != FileType.FILE) {
            continue
        }
        val key = change.file.absolutePath
        val existing = byPath[key]
        if (existing == null ||
            (existing.changeType == ChangeType.REMOVED && change.changeType != ChangeType.REMOVED)
        ) {
            byPath[key] = change
        }
    }
    return byPath.values.filter { change ->
        change.changeType != ChangeType.REMOVED || change.file !in currentSourceFiles
    }
}
