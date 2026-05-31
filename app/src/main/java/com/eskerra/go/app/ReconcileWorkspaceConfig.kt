package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import java.io.File

/** Runs branch reconciliation without blocking app start; returns updated config when changed. */
suspend fun reconcileWorkspaceConfig(
    config: WorkspaceConfig,
    filesDir: File,
    reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch
): WorkspaceConfig = reconcileWorkspaceSyncBranch(config, filesDir).getOrNull() ?: config
