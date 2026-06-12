package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.NoteEditorUiState
import java.io.File
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

class NoteEditorErrorMappingTest {

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
    fun readOnlySaveRejectionMapsToExplicitMessage() = runTest {
        val noteId = NoteId("Inbox/Editable.md")
        val inboxSummary = NoteSummary(noteId, "Editable", "", isInbox = true)
        val readOnlySummary = inboxSummary.copy(isInbox = false)
        val loadRegistry = FakeNoteRegistryRepository.withInboxNotes(inboxSummary)
        val saveRegistry = CountingRegistryRepository(
            responses = listOf(Result.success(NoteRegistry.fromNotes(listOf(readOnlySummary))))
        )
        val content = FakeNoteContentRepository.withContent(noteId, "# Editable")
        val viewModel = NoteEditorViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadEditableNote = LoadEditableNote(loadRegistry, content),
            saveNote = SaveNote(
                writeRepository = FakeNoteWriteRepository(),
                registryRepository = saveRegistry,
                loadGitStatusSummary = loadGitStatusSummary()
            ),
            loadGitStatusSummary = loadGitStatusSummary()
        )

        viewModel.updateDraft("Editable\n\nChanged")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertEquals("This note is read-only.", state.errorMessage)
        assertEquals("Editable\n\nChanged", state.draftMarkdown)
    }

    @Test
    fun registryRefreshFailureMapsToSafeMessage() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val summary = NoteSummary(noteId, "First", "", isInbox = true)
        val loadRegistry = FakeNoteRegistryRepository.withInboxNotes(summary)
        val saveRegistry = CountingRegistryRepository(
            responses = listOf(
                Result.success(NoteRegistry.fromNotes(listOf(summary))),
                Result.failure(NoteIndexException(NoteIndexError.ScanFailed(null)))
            )
        )
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val viewModel = NoteEditorViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadEditableNote = LoadEditableNote(loadRegistry, content),
            saveNote = SaveNote(
                writeRepository = FakeNoteWriteRepository(),
                registryRepository = saveRegistry,
                loadGitStatusSummary = loadGitStatusSummary()
            ),
            loadGitStatusSummary = loadGitStatusSummary()
        )

        viewModel.updateDraft("First\n\nEdited")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as NoteEditorUiState.Content
        assertEquals(NoteEditorViewModel.REGISTRY_REFRESH_ERROR_MESSAGE, state.errorMessage)
        assertEquals("First\n\nEdited", state.draftMarkdown)
    }

    private class CountingRegistryRepository(private val responses: List<Result<NoteRegistry>>) :
        NoteRegistryRepository {
        private var refreshCount = 0

        override suspend fun refresh(
            config: WorkspaceConfig,
            filesDir: File
        ): Result<NoteRegistry> {
            val response = responses.getOrElse(refreshCount) { responses.last() }
            refreshCount += 1
            return response
        }
    }

    private fun loadGitStatusSummary(): LoadGitStatusSummary =
        LoadGitStatusSummary(JGitWorkspaceRepository(), Dispatchers.Main)
}
