package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncStatusSummary

/** UI state for the manual sync screen. */
sealed interface SyncUiState {
    data object Loading : SyncUiState

    data class Ready(val status: SyncStatusSummary, val remoteUri: String?, val branch: String) :
        SyncUiState

    data class Syncing(val status: SyncStatusSummary, val step: SyncProgressStep) : SyncUiState

    data class Success(
        val status: SyncStatusSummary,
        val committed: Boolean,
        val pushed: Boolean,
        val pulled: Boolean
    ) : SyncUiState

    data class Error(val status: SyncStatusSummary?, val message: String) : SyncUiState
}
