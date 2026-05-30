package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncRecoveryAction
import com.eskerra.go.core.model.SyncStatusSummary

/** UI state for the manual sync screen. */
sealed interface SyncUiState {
    data object Loading : SyncUiState

    data class Ready(
        val status: SyncStatusSummary,
        val remoteUri: String?,
        val branch: String,
        val preflight: SyncPreflightSummary,
        val diagnostic: SafeSyncDiagnostic
    ) : SyncUiState

    data class Syncing(val status: SyncStatusSummary, val step: SyncProgressStep) : SyncUiState

    data class Success(
        val status: SyncStatusSummary,
        val committed: Boolean,
        val pushed: Boolean,
        val pulled: Boolean,
        val warningMessage: String? = null
    ) : SyncUiState

    data class Error(
        val status: SyncStatusSummary?,
        val message: String,
        val recoveryAction: SyncRecoveryAction
    ) : SyncUiState
}
