package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.todayhub.TodayHubUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchSettledTest {

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun notSettled_whileGateLoading() {
        assertFalse(
            isLaunchSettled(AppGateState.Loading, inboxUiState = null, todayHubUiState = null)
        )
    }

    @Test
    fun settled_forNeedsSetup_withoutInbox() {
        assertTrue(
            isLaunchSettled(
                AppGateState.NeedsSetup(recoveryMessage = null),
                inboxUiState = null,
                todayHubUiState = null
            )
        )
    }

    @Test
    fun notSettled_whenReadyAndInboxLoading() {
        assertFalse(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Loading,
                todayHubUiState = TodayHubUiState.Empty
            )
        )
    }

    @Test
    fun notSettled_whenReadyAndTodayHubLoading() {
        assertFalse(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Empty,
                todayHubUiState = TodayHubUiState.Loading
            )
        )
    }

    @Test
    fun notSettled_whenReadyAndTodayHubNull() {
        assertFalse(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Empty,
                todayHubUiState = null
            )
        )
    }

    @Test
    fun settled_whenReadyAndBothEmpty() {
        assertTrue(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Empty,
                todayHubUiState = TodayHubUiState.Empty
            )
        )
    }

    @Test
    fun settled_whenReadyAndInboxContent() {
        val note = NoteSummary(
            id = NoteId("Inbox/hello.md"),
            title = "Hello",
            snippet = "",
            isInbox = true
        )
        assertTrue(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Content(listOf(note), isRefreshing = true),
                todayHubUiState = TodayHubUiState.Empty
            )
        )
    }

    @Test
    fun settled_whenReadyAndInboxEmpty() {
        assertTrue(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Empty,
                todayHubUiState = TodayHubUiState.Empty
            )
        )
    }

    @Test
    fun settled_whenReadyAndTodayHubError() {
        assertTrue(
            isLaunchSettled(
                AppGateState.Ready(config),
                inboxUiState = InboxUiState.Empty,
                todayHubUiState = TodayHubUiState.Error("vault scan failed")
            )
        )
    }
}
