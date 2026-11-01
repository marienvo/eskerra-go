package com.eskerra.go.feature.inbox

import androidx.lifecycle.ViewModelStore
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeInboxSnapshotStore
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxSnoozeVisibilityTest {
    @Test
    fun hidesOnlyFutureSnoozes() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val now = System.currentTimeMillis()
            val future = note("future", now + 3_600_000)
            val past = note("past", now - 1_000)
            val cache = NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes(future, past))
            val store = ViewModelStore()
            val model = InboxViewModel(
                config = WorkspaceConfig("Test", "vault", null, "main", 1L),
                filesDir = File("."),
                loadInboxSummaries = LoadInboxSummariesCached(
                    LoadInboxSummaries(cache),
                    FakeInboxSnapshotStore(),
                    cache.registry
                ),
                deleteInboxNotes = DeleteInboxNotes(
                    FakeNoteWriteRepository(),
                    cache,
                    LoadGitStatusSummary(JGitWorkspaceRepository())
                )
            )
            store.put("inbox", model)
            assertEquals(listOf(past), (model.uiState.value as InboxUiState.Content).notes)
            store.clear()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun note(name: String, until: Long) = NoteSummary(
        id = NoteId("Inbox/$name.md"),
        title = name,
        snippet = "",
        isInbox = true,
        snoozedUntilEpochMillis = until
    )
}
