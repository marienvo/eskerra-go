package com.eskerra.go.app

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
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the feedback-loop safety automatic sync depends on: auto-sync success calls
 * `markInboxNotesChanged`, and the inbox route answers that with a plain `refresh()`. If `refresh()`
 * ever called `onInboxMutated`, that would loop (refresh -> onInboxMutated -> another sync trigger ->
 * another refresh -> ...). `onInboxMutated` must fire only from `deleteSelected()`'s success path.
 */
class InboxViewModelFeedbackLoopTest {

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
    fun refresh_neverInvokesOnInboxMutated() = runTest {
        val filesDir = temp.newFolder("files")
        val note = NoteSummary(
            id = NoteId("Inbox/hello.md"),
            title = "Hello",
            snippet = "Body",
            isInbox = true
        )
        val repository = FakeNoteRegistryRepository.withInboxNotes(note)
        val mutations = mutableListOf<List<String>>()
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
            ),
            onInboxMutated = { mutations += it }
        )

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(mutations.isEmpty())
    }
}
