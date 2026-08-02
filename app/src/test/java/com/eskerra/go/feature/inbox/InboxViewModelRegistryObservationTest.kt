package com.eskerra.go.feature.inbox

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
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
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.inbox.InboxUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the sync→inbox bridge: ManualSyncNow refreshes NoteRegistryCache directly, and the inbox
 * must drop deleted notes from that shared registry without waiting for an explicit refresh()
 * (the Compose inboxRefreshSignal path can be missed while the list stays on screen).
 */
class InboxViewModelRegistryObservationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun registryUpdate_withoutRefresh_dropsDeletedInboxNote() = runTest {
        val filesDir = temp.newFolder("files")
        val kept = NoteSummary(
            id = NoteId("Inbox/kept.md"),
            title = "Kept",
            snippet = "",
            isInbox = true
        )
        val deleted = NoteSummary(
            id = NoteId("Inbox/deleted.md"),
            title = "Deleted",
            snippet = "",
            isInbox = true
        )
        val repository = FakeNoteRegistryRepository.withInboxNotes(kept, deleted)
        val cache = NoteRegistryCache(repository)
        val viewModel = InboxViewModel(
            config = config,
            filesDir = filesDir,
            loadInboxSummaries = LoadInboxSummariesCached(
                delegate = LoadInboxSummaries(cache),
                snapshotStore = FakeInboxSnapshotStore(),
                registryUpdates = cache.registry
            ),
            deleteInboxNotes = DeleteInboxNotes(
                writeRepository = FakeNoteWriteRepository(),
                registryCache = cache,
                loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
            )
        )
        advanceUntilIdle()

        val before = viewModel.uiState.value as InboxUiState.Content
        assertEquals(setOf(kept.id, deleted.id), before.notes.map { it.id }.toSet())

        // Simulate ManualSyncNow's post-pull registry refresh: publish a smaller registry without
        // calling InboxViewModel.refresh().
        repository.setResult(Result.success(NoteRegistry.fromNotes(listOf(kept))))
        cache.invalidate(config, filesDir)
        cache.refresh(config, filesDir)
        advanceUntilIdle()

        val after = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(kept), after.notes)
    }
}
