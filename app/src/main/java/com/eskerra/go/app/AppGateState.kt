package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig

/** Root-level gate state. Setup is not a NavHost route. */
sealed interface AppGateState {
    data object Loading : AppGateState
    data class NeedsSetup(val recoveryMessage: String? = null) : AppGateState
    data class Ready(val config: WorkspaceConfig) : AppGateState
}
