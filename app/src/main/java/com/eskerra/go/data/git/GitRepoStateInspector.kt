package com.eskerra.go.data.git

import java.io.File

/** Detects Git repository states that require manual repair before sync. */
object GitRepoStateInspector {

    fun requiresManualIntervention(workingDir: File): Boolean {
        val gitDir = File(workingDir, ".git")
        if (!gitDir.isDirectory) return false
        return File(gitDir, "MERGE_HEAD").isFile ||
            File(gitDir, "CHERRY_PICK_HEAD").isFile ||
            File(gitDir, "REVERT_HEAD").isFile ||
            File(gitDir, "rebase-merge").isDirectory ||
            File(gitDir, "rebase-apply").isDirectory
    }
}
