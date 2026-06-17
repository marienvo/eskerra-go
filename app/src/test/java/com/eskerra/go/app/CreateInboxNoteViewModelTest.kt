package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.CreateInboxUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun initDoesNotCreateNote() = runTest {
        val viewModel = createViewModel()

        assertNull(viewModel.savedNoteId.value)
        val state = viewModel.uiState.value as CreateInboxUiState.Content
        assertEquals("", state.draft)
        assertFalse(state.canSave)
    }

    @Test
    fun saveEmitsNoteIdForReaderNavigation() = runTest {
        val noteId = NoteId("Inbox/Mijn idee.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "Mijn idee", "", isInbox = true, lastModifiedEpochMillis = 1L)
        )
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryCache = NoteRegistryCache(registry),
            loadGitStatusSummary = loadGitStatusSummary()
        )
        val viewModel = CreateInboxNoteViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            createInboxNote = useCase
        )
        viewModel.updateDraft("Mijn idee\nBody")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(noteId, viewModel.savedNoteId.value)
    }

    @Test
    fun saveFailureShowsErrorState() = runTest {
        val registry = FakeNoteRegistryRepository.failing()
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryCache = NoteRegistryCache(registry),
            loadGitStatusSummary = loadGitStatusSummary()
        )
        val viewModel = CreateInboxNoteViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            createInboxNote = useCase
        )
        viewModel.updateDraft("My idea")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as CreateInboxUiState.Content
        assertEquals(CreateInboxNoteViewModel.CREATE_ERROR_MESSAGE, state.errorMessage)
        assertFalse(state.isSaving)
    }

    private fun createViewModel(): CreateInboxNoteViewModel {
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryCache = NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes()),
            loadGitStatusSummary = loadGitStatusSummary()
        )
        return CreateInboxNoteViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            createInboxNote = useCase
        )
    }

    private fun loadGitStatusSummary(): LoadGitStatusSummary =
        LoadGitStatusSummary(JGitWorkspaceRepository(), Dispatchers.Main)
}
