package com.eskerra.go.app

import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.SyncRecoveryAction
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.feature.sync.SyncUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuSyncActionTest {

    @Test
    fun readyWithCanSync_syncsNow() {
        assertEquals(MenuSyncAction.SyncNow, menuSyncAction(readyState(canSync = true)))
    }

    @Test
    fun readyWithoutCanSync_opensSyncScreen() {
        assertEquals(MenuSyncAction.OpenSyncScreen, menuSyncAction(readyState(canSync = false)))
    }

    @Test
    fun loading_isNoOp() {
        assertEquals(MenuSyncAction.NoOp, menuSyncAction(SyncUiState.Loading))
    }

    @Test
    fun syncing_isNoOp() {
        assertEquals(
            MenuSyncAction.NoOp,
            menuSyncAction(
                SyncUiState.Syncing(
                    status = status(),
                    step = com.eskerra.go.core.model.SyncProgressStep.FetchingRemote
                )
            )
        )
    }

    @Test
    fun success_refreshesLocalStatus() {
        assertEquals(
            MenuSyncAction.RefreshLocalStatus,
            menuSyncAction(
                SyncUiState.Success(
                    status = status(),
                    committed = false,
                    pushed = false,
                    pulled = true
                )
            )
        )
    }

    @Test
    fun error_opensSyncScreen() {
        assertEquals(
            MenuSyncAction.OpenSyncScreen,
            menuSyncAction(
                SyncUiState.Error(
                    status = status(),
                    message = "Failed",
                    recoveryAction = SyncRecoveryAction(hint = "Retry")
                )
            )
        )
    }

    private fun readyState(canSync: Boolean): SyncUiState.Ready = SyncUiState.Ready(
        status = status(),
        remoteUri = "https://example.com/repo.git",
        branch = "main",
        preflight = preflight(canSync),
        diagnostic = diagnostic()
    )

    private fun status() = SyncStatusSummary(
        state = SyncStatusState.Clean,
        branch = "main",
        changedCount = 0,
        aheadCount = 0,
        behindCount = 0,
        message = "Status"
    )

    private fun preflight(canSync: Boolean) = SyncPreflightSummary(
        canSync = canSync,
        blockReason = if (canSync) null else SyncError.MissingCredential,
        workspaceReady = true,
        remoteConfigured = true,
        credentialPresent = true,
        inboxChangeCount = 0,
        nonInboxChangeCount = 0,
        unsafePathCount = 0,
        stagedNonInboxCount = 0,
        stagedUnsafeCount = 0,
        aheadCount = 0,
        behindCount = 0,
        repoInterventionRequired = !canSync,
        userMessage = if (canSync) "Ready" else "Blocked"
    )

    private fun diagnostic() = SafeSyncDiagnostic(
        sanitizedRemote = "example.com/repo",
        branch = "main",
        inboxChangeCount = 0,
        nonInboxChangeCount = 0,
        aheadCount = 0,
        behindCount = 0,
        lastSync = null
    )
}
