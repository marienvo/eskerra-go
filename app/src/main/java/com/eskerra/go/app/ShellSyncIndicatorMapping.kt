package com.eskerra.go.app

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.displayLabel
import com.eskerra.go.feature.sync.SyncUiState

internal fun shellSyncIndicatorState(
    syncState: SyncUiState,
    remoteConfigured: Boolean
): ShellSyncIndicatorState? {
    if (!remoteConfigured) {
        return null
    }

    val status = when (syncState) {
        SyncUiState.Loading -> return null
        is SyncUiState.Ready -> syncState.status
        is SyncUiState.Syncing -> syncState.status
        is SyncUiState.Success -> syncState.status
        is SyncUiState.Error -> syncState.status
            ?: return ShellSyncIndicatorState(badgeText = "!", changeCount = null)
    }
    return ShellSyncIndicatorState(
        badgeText = badgeTextFor(status),
        changeCount = changeCountFor(status)
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

/** Numeric pending count for the menu's sync entry (the badge's number, without the "!" fallback). */
private fun changeCountFor(status: SyncStatusSummary): Int? = when (status.state) {
    SyncStatusState.Behind -> status.behindCount.takeIf { it > 0 }
    SyncStatusState.Ahead -> status.aheadCount.takeIf { it > 0 }
    SyncStatusState.DirtyLocalChanges -> status.changedCount.takeIf { it > 0 }
    else -> null
}

internal fun syncStatusLabel(syncState: SyncUiState): String = when (syncState) {
    SyncUiState.Loading -> "Checking…"
    is SyncUiState.Ready -> syncState.status.displayLabel()
    is SyncUiState.Syncing -> "Syncing…"
    is SyncUiState.Success -> syncState.status.displayLabel()
    is SyncUiState.Error -> syncState.status?.displayLabel() ?: "Error"
}
