package com.eskerra.go.data.workspace

import com.eskerra.go.app.AppGateState
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/**
 * Pure gate resolution for app launch. Testable without Compose or DataStore.
 */
fun resolveAppGateState(config: WorkspaceConfig?, filesDir: File): AppGateState {
    if (config == null) {
        return AppGateState.NeedsSetup()
    }

    val pathResult = WorkspacePaths.resolve(filesDir, config.relativePath)
    if (pathResult.isFailure) {
        return AppGateState.NeedsSetup(
            recoveryMessage = pathResult.exceptionOrNull()?.message
                ?: "Stored workspace path is invalid",
        )
    }

    val workspaceDir = pathResult.getOrThrow()
    if (!WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
        return AppGateState.NeedsSetup(
            recoveryMessage = "Workspace repository is missing or invalid",
        )
    }

    return AppGateState.Ready(config)
}
