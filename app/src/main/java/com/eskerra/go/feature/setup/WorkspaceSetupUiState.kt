package com.eskerra.go.feature.setup

import com.eskerra.go.data.workspace.WorkspaceSetupMode

data class WorkspaceSetupUiState(
    val mode: WorkspaceSetupMode = WorkspaceSetupMode.InitializeLocal,
    val name: String = "",
    val branch: String = "main",
    val remoteUri: String = "",
    val credential: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val recoveryMessage: String? = null,
)
