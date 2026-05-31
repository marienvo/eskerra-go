package com.eskerra.go.app

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.displayLabel
import com.eskerra.go.core.model.hasSyncWork
import com.eskerra.go.core.model.needsAttention
import com.eskerra.go.feature.sync.SyncUiState

internal fun shellSyncIndicatorState(
    syncState: SyncUiState,
    remoteConfigured: Boolean
): ShellSyncIndicatorState? {
    if (!remoteConfigured) {
        return null
    }

    return when (syncState) {
        SyncUiState.Loading -> ShellSyncIndicatorState(
            needsAttention = false,
            isEnabled = false,
            isChecking = true,
            isSyncing = false,
            badgeText = null
        )

        is SyncUiState.Ready -> indicatorFromStatus(
            syncState.status,
            isChecking = false,
            isSyncing = false
        )

        is SyncUiState.Syncing -> indicatorFromStatus(
            syncState.status,
            isChecking = false,
            isSyncing = true
        )

        is SyncUiState.Success -> indicatorFromStatus(
            syncState.status,
            isChecking = false,
            isSyncing = false
        )

        is SyncUiState.Error -> syncState.status?.let {
            indicatorFromStatus(it, isChecking = false, isSyncing = false)
        } ?: ShellSyncIndicatorState(
            needsAttention = true,
            isEnabled = true,
            isChecking = false,
            isSyncing = false,
            badgeText = "!"
        )
    }
}

private fun indicatorFromStatus(
    status: SyncStatusSummary,
    isChecking: Boolean,
    isSyncing: Boolean
): ShellSyncIndicatorState {
    val busy = isChecking || isSyncing
    return ShellSyncIndicatorState(
        needsAttention = status.needsAttention,
        isEnabled = status.hasSyncWork && !busy,
        isChecking = isChecking,
        isSyncing = isSyncing,
        badgeText = badgeTextFor(status)
    )
}

private fun badgeTextFor(status: SyncStatusSummary): String? = when (status.state) {
    SyncStatusState.Behind -> status.behindCount.takeIf { it > 0 }?.toString()
    SyncStatusState.Ahead -> status.aheadCount.takeIf { it > 0 }?.toString()
    SyncStatusState.DirtyLocalChanges -> status.changedCount.takeIf { it > 0 }?.toString() ?: "!"
    SyncStatusState.Diverged,
    SyncStatusState.ConflictRisk,
    SyncStatusState.Error -> "!"
    SyncStatusState.Clean,
    SyncStatusState.Unavailable -> null
}

internal fun syncStatusLabel(syncState: SyncUiState): String = when (syncState) {
    SyncUiState.Loading -> "Checking…"
    is SyncUiState.Ready -> syncState.status.displayLabel()
    is SyncUiState.Syncing -> "Syncing…"
    is SyncUiState.Success -> syncState.status.displayLabel()
    is SyncUiState.Error -> syncState.status?.displayLabel() ?: "Error"
}
