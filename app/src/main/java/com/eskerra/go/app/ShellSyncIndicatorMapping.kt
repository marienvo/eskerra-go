package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.displayLabel
import com.eskerra.go.feature.sync.AppSyncViewModel
import com.eskerra.go.feature.sync.SyncUiState

/** Collects [AppSyncViewModel]'s sync + spinner state and maps it to [ShellSyncIndicatorState]. */
@Composable
internal fun rememberShellSyncIndicator(
    appSyncViewModel: AppSyncViewModel,
    remoteConfigured: Boolean
): ShellSyncIndicatorState? {
    val syncState by appSyncViewModel.uiState.collectAsState()
    val spinning by appSyncViewModel.syncSpinnerVisible.collectAsState()
    return shellSyncIndicatorState(syncState, remoteConfigured, spinning)
}

internal fun shellSyncIndicatorState(
    syncState: SyncUiState,
    remoteConfigured: Boolean,
    spinning: Boolean = false
): ShellSyncIndicatorState? {
    if (!remoteConfigured) {
        return null
    }

    val status = when (syncState) {
        SyncUiState.Loading -> return null
        is SyncUiState.Ready -> syncState.status
        is SyncUiState.Syncing -> syncState.status
        is SyncUiState.Success -> syncState.status
        is SyncUiState.Error ->
            syncState.status
                ?: return ShellSyncIndicatorState(
                    badgeText = "!",
                    changeCount = null,
                    spinning = spinning
                )
    }
    return ShellSyncIndicatorState(
        badgeText = badgeTextFor(status),
        changeCount = changeCountFor(status),
        spinning = spinning
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
