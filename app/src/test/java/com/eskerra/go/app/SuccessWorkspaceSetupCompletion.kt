package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceSetupMode
import java.io.File

/** Succeeds immediately for ViewModel success-path tests without Git or IO. */
class SuccessWorkspaceSetupCompletion : WorkspaceSetupCompletion {
    override suspend fun completeAndPersist(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = Result.success(
        WorkspaceConfig(
            name = name.trim(),
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri?.trim()?.ifBlank { null },
            branch = branch.trim().ifBlank { "master" },
            setupCompletedAtEpochMs = 1_700_000_000_000L
        )
    )
}
