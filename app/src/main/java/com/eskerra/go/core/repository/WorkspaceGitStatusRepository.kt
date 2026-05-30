package com.eskerra.go.core.repository

import com.eskerra.go.core.model.GitWorkspaceStatus
import java.io.File

/** Reads working-tree Git status for a configured workspace directory. */
interface WorkspaceGitStatusRepository {
    fun status(workingDir: File): Result<GitWorkspaceStatus>
}
