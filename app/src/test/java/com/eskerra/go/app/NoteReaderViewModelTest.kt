package com.eskerra.go.app

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderSegment
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.note.NoteReaderUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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

class NoteReaderViewModelTest {

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
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, content)
        )

        assertEquals(NoteReaderUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun successStateContainsTitlePathAndSegments() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val secondId = NoteId("Second.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            summary(noteId, "First"),
            summary(secondId, "Second")
        )
        val content = FakeNoteContentRepository.withContent(noteId, "Hello [[Second]].")
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, content)
        )

        val state = viewModel.uiState.value as NoteReaderUiState.Content
        assertEquals("First", state.title)
        assertEquals(noteId, state.noteId)
        assertEquals("Inbox/First.md", state.path)
        assertTrue(state.document.segments.any { it is NoteReaderSegment.ResolvedLink })
    }

    @Test
    fun notFoundStateWhenRegistryMissesNote() = runTest {
        val noteId = NoteId("Inbox/Missing.md")
        val registry = FakeNoteRegistryRepository()
        val content = FakeNoteContentRepository.withContent(noteId, "# Missing")
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, content)
        )

        assertEquals(NoteReaderUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun errorStateWhenContentReadFails() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.failing(NoteContentError.ReadFailed("disk"))
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, content)
        )

        assertEquals(
            NoteReaderUiState.Error(NoteReaderViewModel.READ_ERROR_MESSAGE),
            viewModel.uiState.value
        )
    }

    @Test
    fun invalidNoteIdStateForBlankPath() = runTest {
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = NoteId(""),
            loadNoteForReading = LoadNoteForReading(
                FakeNoteRegistryRepository(),
                FakeNoteContentRepository()
            )
        )

        assertEquals(NoteReaderUiState.InvalidNoteId, viewModel.uiState.value)
    }

    @Test
    fun workspaceMissingMapsToSafeMessage() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.failing(
            com.eskerra.go.data.notes.NoteIndexError.WorkspaceMissing(null)
        )
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, FakeNoteContentRepository())
        )

        assertEquals(
            NoteReaderUiState.Error(NoteReaderViewModel.WORKSPACE_MISSING_MESSAGE),
            viewModel.uiState.value
        )
    }

    @Test
    fun retryReloadsAfterFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.failing(NoteContentError.ReadFailed("disk"))
        val viewModel = NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(registry, content)
        )
        dispatcher.scheduler.runCurrent()
        assertEquals(
            NoteReaderUiState.Error(NoteReaderViewModel.READ_ERROR_MESSAGE),
            viewModel.uiState.value
        )

        content.setResult(
            Result.success(
                com.eskerra.go.core.model.NoteContent(
                    noteId,
                    com.eskerra.go.core.model.NotePath.fromRelativePath(noteId.value).getOrThrow(),
                    "# First"
                )
            )
        )
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NoteReaderUiState.Content)
    }

    private fun summary(id: NoteId, title: String): NoteSummary =
        NoteSummary(id = id, title = title, snippet = "", isInbox = true)
}
