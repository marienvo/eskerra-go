package com.eskerra.go.data.git

import java.io.File
import org.eclipse.jgit.api.Git

/** Reads staged paths from the Git index (cached changes vs HEAD). */
object GitIndexInspector {

    fun readStagedPaths(workingDir: File): Result<Set<String>> = runCatching {
        Git.open(workingDir).use { git ->
            val status = git.status().call()
            buildSet {
                addAll(status.added)
                addAll(status.changed)
                addAll(status.removed)
            }
        }
    }
}
