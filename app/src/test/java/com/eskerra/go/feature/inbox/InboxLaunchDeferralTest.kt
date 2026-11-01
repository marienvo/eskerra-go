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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxLaunchDeferralTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun noSnapshotStillLetsLaunchSettleBeforeScanning() = runTest {
        val note = NoteSummary(NoteId("Inbox/note.md"), "Note", "", true)
        val repository = FakeNoteRegistryRepository.withInboxNotes(note)
        val cache = NoteRegistryCache(repository)
        val loader = LoadInboxSummariesCached(LoadInboxSummaries(cache), FakeInboxSnapshotStore())
        loader.deferUntilLaunchSettled()
        val model = InboxViewModel(
            WorkspaceConfig("Test", "vault", null, "main", 1),
            File("."),
            loader,
            DeleteInboxNotes(
                FakeNoteWriteRepository(),
                cache,
                LoadGitStatusSummary(JGitWorkspaceRepository())
            )
        )
        val store = ViewModelStore()
        store.put("inbox", model)
        assertTrue(
            model.uiState.value is InboxUiState.Content || model.uiState.value is InboxUiState.Empty
        )
        assertEquals(0, repository.refreshCount)
        loader.onLaunchSettled()
        assertEquals(listOf(note), (model.uiState.value as InboxUiState.Content).notes)
        assertEquals(1, repository.refreshCount)
        store.clear()
    }
}
