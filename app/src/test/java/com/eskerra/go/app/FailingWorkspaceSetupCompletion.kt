package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceSetupError
import com.eskerra.go.data.workspace.WorkspaceSetupException
import com.eskerra.go.data.workspace.WorkspaceSetupMode
import java.io.File

/** Fails immediately for ViewModel error-path tests without Git or IO. */
class FailingWorkspaceSetupCompletion : WorkspaceSetupCompletion {
    override suspend fun completeAndPersist(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = Result.failure(
        WorkspaceSetupException(WorkspaceSetupError.MetadataSaveFailed)
    )
}
