package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.data.notes.FakeInboxSnapshotStore
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InboxViewModelTest {

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
    fun init_withInboxNotes_movesToContent() = runTest {
        val filesDir = temp.newFolder("files")
        val note = NoteSummary(
            id = NoteId("Inbox/hello.md"),
            title = "Hello",
            snippet = "Body",
            isInbox = true
        )
        val repository = FakeNoteRegistryRepository.withInboxNotes(note)
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(note), state.notes)
        assertFalse(state.isRefreshing)
        assertSame(config, repository.lastConfig)
        assertSame(filesDir, repository.lastFilesDir)
    }

    @Test
    fun init_withCachedNotes_skipsInitialLoading() = runTest {
        val filesDir = temp.newFolder("files")
        val cachedNote = NoteSummary(
            id = NoteId("Inbox/cached.md"),
            title = "Cached",
            snippet = "",
            isInbox = true
        )
        val repository = FakeNoteRegistryRepository(
            result = Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(listOf(cachedNote))
            ),
            refreshDelayMs = 1_000L
        )
        val snapshotStore = FakeInboxSnapshotStore()
        snapshotStore.save(config, filesDir, listOf(cachedNote))

        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository,
            snapshotStore = snapshotStore
        )
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(cachedNote), state.notes)
        assertTrue(state.isRefreshing)
        assertTrue(viewModel.uiState.value !is InboxUiState.Loading)
    }

    @Test
    fun init_withNoInboxNotes_movesToEmpty() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FakeNoteRegistryRepository()
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )

        assertEquals(InboxUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun init_whenLoadFails_movesToError() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FakeNoteRegistryRepository.failing()
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )

        assertEquals(
            InboxUiState.Error(InboxViewModel.SCAN_ERROR_MESSAGE),
            viewModel.uiState.value
        )
    }

    @Test
    fun init_whenWorkspaceMissing_mapsToMissingMessage() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FakeNoteRegistryRepository.failing(NoteIndexError.WorkspaceMissing(null))
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )

        assertEquals(
            InboxUiState.Error(InboxViewModel.WORKSPACE_MISSING_MESSAGE),
            viewModel.uiState.value
        )
    }

    @Test
    fun init_whenInvalidWorkspacePath_mapsToUnavailableMessage() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FakeNoteRegistryRepository.failing(
            NoteIndexError.InvalidWorkspacePath(null)
        )
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )

        assertEquals(
            InboxUiState.Error(InboxViewModel.WORKSPACE_UNAVAILABLE_MESSAGE),
            viewModel.uiState.value
        )
    }

    @Test
    fun emptyStateIsDistinctFromScanFailure() = runTest {
        val filesDir = temp.newFolder("files")
        val emptyViewModel = inboxViewModel(
            filesDir = filesDir,
            repository = FakeNoteRegistryRepository()
        )
        val failingViewModel = inboxViewModel(
            filesDir = filesDir,
            repository = FakeNoteRegistryRepository.failing()
        )

        assertEquals(InboxUiState.Empty, emptyViewModel.uiState.value)
        assertTrue(failingViewModel.uiState.value is InboxUiState.Error)
    }

    @Test
    fun refresh_retriesLoad() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FakeNoteRegistryRepository.failing()
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )
        assertEquals(1, repository.refreshCount)

        val note = NoteSummary(
            id = NoteId("Inbox/retry.md"),
            title = "Retry",
            snippet = "",
            isInbox = true
        )
        repository.setResult(
            Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(listOf(note))
            )
        )
        viewModel.refresh()

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(note), state.notes)
        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun refresh_cancelsInFlightLoad() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val filesDir = temp.newFolder("files")
        val staleNote = NoteSummary(
            id = NoteId("Inbox/stale.md"),
            title = "Stale",
            snippet = "",
            isInbox = true
        )
        val freshNote = NoteSummary(
            id = NoteId("Inbox/fresh.md"),
            title = "Fresh",
            snippet = "",
            isInbox = true
        )
        val repository = FakeNoteRegistryRepository(
            result = Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(listOf(staleNote))
            ),
            refreshDelayMs = 1_000L
        )
        val viewModel = inboxViewModel(
            filesDir = filesDir,
            repository = repository
        )
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.refreshCount)

        repository.setResult(
            Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(listOf(freshNote))
            )
        )
        repository.setRefreshDelayMs(0L)
        viewModel.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repository.refreshCount)

        advanceUntilIdle()

        val state = viewModel.uiState.value as InboxUiState.Content
        assertEquals(listOf(freshNote), state.notes)
    }

    private fun inboxViewModel(
        filesDir: File,
        repository: FakeNoteRegistryRepository,
        snapshotStore: FakeInboxSnapshotStore = FakeInboxSnapshotStore()
    ): InboxViewModel = InboxViewModel(
        config = config,
        filesDir = filesDir,
        loadInboxSummaries = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(repository),
            snapshotStore = snapshotStore
        )
    )
}
