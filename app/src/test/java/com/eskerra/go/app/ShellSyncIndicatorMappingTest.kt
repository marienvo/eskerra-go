package com.eskerra.go.app

import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.feature.sync.SyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellSyncIndicatorMappingTest {

    @Test
    fun returnsNull_whenRemoteNotConfigured() {
        assertNull(shellSyncIndicatorState(SyncUiState.Loading, remoteConfigured = false))
    }

    @Test
    fun returnsNull_whenLoadingAndRemoteConfigured() {
        assertNull(shellSyncIndicatorState(SyncUiState.Loading, remoteConfigured = true))
    }

    @Test
    fun cleanReadyState_hasNoBadgeOrCount() {
        val state = readyStatus(SyncStatusState.Clean)

        val indicator = shellSyncIndicatorState(state, remoteConfigured = true)!!

        assertNull(indicator.badgeText)
        assertNull(indicator.changeCount)
    }

    @Test
    fun dirtyReadyState_exposesBadgeAndCount() {
        val status = SyncStatusSummary(
            state = SyncStatusState.DirtyLocalChanges,
            branch = "main",
            changedCount = 2,
            aheadCount = 0,
            behindCount = 0,
            message = "Local changes."
        )
        val state = SyncUiState.Ready(
            status = status,
            remoteUri = "https://example.com/repo.git",
            branch = "main",
            preflight = preflight(),
            diagnostic = diagnostic()
        )

        val indicator = shellSyncIndicatorState(state, remoteConfigured = true)!!

        assertEquals("2", indicator.badgeText)
        assertEquals(2, indicator.changeCount)
    }

    @Test
    fun syncingState_usesCurrentStatusBadgeAndCount() {
        val state = SyncUiState.Syncing(
            status = dirtyStatus(),
            step = com.eskerra.go.core.model.SyncProgressStep.FetchingRemote
        )

        val indicator = shellSyncIndicatorState(state, remoteConfigured = true)!!

        assertEquals("1", indicator.badgeText)
        assertEquals(1, indicator.changeCount)
    }

    private fun readyStatus(syncState: SyncStatusState): SyncUiState.Ready = SyncUiState.Ready(
        status = SyncStatusSummary(
            state = syncState,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Status"
        ),
        remoteUri = "https://example.com/repo.git",
        branch = "main",
        preflight = preflight(),
        diagnostic = diagnostic()
    )

    private fun dirtyStatus() = SyncStatusSummary(
        state = SyncStatusState.DirtyLocalChanges,
        branch = "main",
        changedCount = 1,
        aheadCount = 0,
        behindCount = 0,
        message = "Local changes."
    )

    private fun preflight() = SyncPreflightSummary(
        canSync = true,
        blockReason = null,
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
        repoInterventionRequired = false,
        userMessage = "Ready to sync."
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
