package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.CreateInboxUiState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CreateInboxNoteViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val fixedInstant = Instant.parse("2026-05-30T16:42:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun createEmitsNoteIdForEditorNavigation() = runTest {
        val noteId = NoteId("Inbox/2026-05-30-164200.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "Untitled inbox note", "", isInbox = true)
        )
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryRepository = registry,
            loadGitStatusSummary = loadGitStatusSummary(),
            clock = fixedClock
        )
        val viewModel = CreateInboxNoteViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            createInboxNote = useCase
        )
        advanceUntilIdle()

        assertEquals(noteId, viewModel.createdNoteId.value)
    }

    @Test
    fun createFailureShowsErrorState() = runTest {
        val registry = FakeNoteRegistryRepository.failing()
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryRepository = registry,
            loadGitStatusSummary = loadGitStatusSummary(),
            clock = fixedClock
        )
        val viewModel = CreateInboxNoteViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            createInboxNote = useCase
        )

        val state = viewModel.uiState.value
        assertTrue(state is CreateInboxUiState.Error)
        assertEquals(
            CreateInboxNoteViewModel.CREATE_ERROR_MESSAGE,
            (state as CreateInboxUiState.Error).message
        )
    }

    private fun loadGitStatusSummary(): LoadGitStatusSummary =
        LoadGitStatusSummary(JGitWorkspaceRepository(), Dispatchers.Main)
}
