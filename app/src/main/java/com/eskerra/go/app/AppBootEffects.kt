package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import java.io.File

@Composable
internal fun AppBootEffects(
    config: WorkspaceConfig,
    filesDir: File,
    reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch,
    appSyncViewModel: AppSyncViewModel,
    onConfigUpdated: (WorkspaceConfig) -> Unit,
    onConfigChanged: (WorkspaceConfig) -> Unit
) {
    LaunchedEffect(config) {
        appSyncViewModel.refreshShellStatusQuietly(forceRemote = true)
    }

    LaunchedEffect(config) {
        val reconciled = reconcileWorkspaceConfig(
            config = config,
            filesDir = filesDir,
            reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch
        )
        if (reconciled != config) {
            onConfigChanged(reconciled)
            onConfigUpdated(reconciled)
        }
    }
}
