package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.NoteEditorUiState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteEditorViewModelTest {

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
    fun startsInLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        content.setLoadDelayMs(1_000L)
        val viewModel = editorViewModel(noteId, registry, content)

        assertEquals(NoteEditorUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun displaysLoadedContentWithGitStatus() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First\n\nBody")
        val viewModel = editorViewModel(noteId, registry, content)

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertEquals("# First\n\nBody", state.draftMarkdown)
        assertFalse(state.isDirty)
        assertTrue(state.note.canEdit)
    }

    @Test
    fun updatesDraftAndDirtyFlag() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val viewModel = editorViewModel(noteId, registry, content)

        viewModel.updateDraft("# First\n\nEdited")

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertEquals("# First\n\nEdited", state.draftMarkdown)
        assertTrue(state.isDirty)
    }

    @Test
    fun saveTransitionsToSavedState() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val writeRepository = FakeNoteWriteRepository()
        val viewModel = editorViewModel(noteId, registry, content, writeRepository)

        viewModel.updateDraft("# First\n\nSaved body")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertFalse(state.isSaving)
        assertFalse(state.isDirty)
        assertEquals(NoteEditorViewModel.SAVED_MESSAGE, state.saveMessage)
    }

    @Test
    fun failedSaveKeepsDraft() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val writeRepository = FakeNoteWriteRepository.failing(
            com.eskerra.go.core.model.NoteWriteError.WriteFailed("disk")
        )
        val saveNote = SaveNote(
            writeRepository = writeRepository,
            registryRepository = registry,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )
        val viewModel = NoteEditorViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadEditableNote = LoadEditableNote(registry, content),
            saveNote = saveNote,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        viewModel.updateDraft("# First\n\nDraft kept")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertEquals("# First\n\nDraft kept", state.draftMarkdown)
        assertTrue(state.isDirty)
        assertEquals(NoteEditorViewModel.SAVE_ERROR_MESSAGE, state.errorMessage)
    }

    @Test
    fun readOnlyStateForNonInboxNote() = runTest {
        val noteId = NoteId("Projects/Plan.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "Plan", "", isInbox = false)
        )
        val content = FakeNoteContentRepository.withContent(noteId, "# Plan")
        val viewModel = editorViewModel(noteId, registry, content)

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertFalse(state.note.canEdit)
    }

    @Test
    fun notFoundStateWhenRegistryMissesNote() = runTest {
        val noteId = NoteId("Inbox/Missing.md")
        val viewModel = editorViewModel(
            noteId,
            FakeNoteRegistryRepository(),
            FakeNoteContentRepository.withContent(noteId, "# Missing")
        )

        assertEquals(NoteEditorUiState.NotFound, viewModel.uiState.value)
    }

    private fun editorViewModel(
        noteId: NoteId,
        registry: FakeNoteRegistryRepository,
        content: FakeNoteContentRepository,
        writeRepository: FakeNoteWriteRepository = FakeNoteWriteRepository()
    ): NoteEditorViewModel = NoteEditorViewModel(
        config = config,
        filesDir = temp.newFolder("files"),
        noteId = noteId,
        loadEditableNote = LoadEditableNote(registry, content),
        saveNote = SaveNote(
            writeRepository = writeRepository,
            registryRepository = registry,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        ),
        loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
    )

    private fun summary(id: NoteId, title: String): NoteSummary =
        NoteSummary(id = id, title = title, snippet = "", isInbox = true)
}
