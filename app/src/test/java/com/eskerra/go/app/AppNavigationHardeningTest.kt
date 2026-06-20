package com.eskerra.go.app

import androidx.lifecycle.SavedStateHandle
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.NoteEditorUiState
import com.eskerra.go.feature.note.NoteReaderUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** Route decode and invalid note id handling without Compose UI. */
class AppNavigationHardeningTest {

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
    fun blankRouteArgument_mapsToInvalidReaderState() = runTest {
        val noteId = AppRoute.decodeNoteId("")
        val viewModel = readerViewModel(noteId)

        assertEquals(NoteReaderUiState.InvalidNoteId, viewModel.uiState.value)
    }

    @Test
    fun traversalLikeRouteArgument_mapsToInvalidReaderState() = runTest {
        val noteId = AppRoute.decodeNoteId(NoteRouteCodec.encode("../secret.md"))
        val viewModel = readerViewModel(noteId)

        assertEquals(NoteReaderUiState.InvalidNoteId, viewModel.uiState.value)
    }

    @Test
    fun blankEditorRouteArgument_mapsToInvalidEditorState() = runTest {
        val noteId = AppRoute.decodeEditorNoteId("")
        val viewModel = editorViewModel(noteId)

        assertEquals(NoteEditorUiState.InvalidNoteId, viewModel.uiState.value)
    }

    @Test
    fun traversalLikeEditorRouteArgument_mapsToInvalidEditorState() = runTest {
        val noteId = AppRoute.decodeEditorNoteId(NoteRouteCodec.encode("Inbox/../secret.md"))
        val viewModel = editorViewModel(noteId)

        assertEquals(NoteEditorUiState.InvalidNoteId, viewModel.uiState.value)
    }

    @Test
    fun consumeNoteReaderChanged_retriesOnlyForCurrentReaderRoute() {
        val noteId = NoteId("Inbox/First.md")
        val savedStateHandle = SavedStateHandle(
            mapOf(NOTE_CONTENT_CHANGED_KEY to true)
        )

        assertFalse(
            consumeNoteReaderChanged(
                currentRoute = AppRoute.INBOX,
                noteId = noteId,
                savedStateHandle = savedStateHandle
            )
        )
        assertTrue(
            consumeNoteReaderChanged(
                currentRoute = AppRoute.NOTE_PATTERN,
                noteId = noteId,
                savedStateHandle = savedStateHandle
            )
        )
        assertFalse(
            consumeNoteReaderChanged(
                currentRoute = AppRoute.NOTE_PATTERN,
                noteId = noteId,
                savedStateHandle = savedStateHandle
            )
        )
    }

    @Test
    fun consumeNoteReaderChanged_doesNotMatchConcreteNoteRoute() {
        val noteId = NoteId("Inbox/First.md")
        val savedStateHandle = SavedStateHandle(
            mapOf(NOTE_CONTENT_CHANGED_KEY to true)
        )

        assertFalse(
            consumeNoteReaderChanged(
                currentRoute = AppRoute.note(noteId),
                noteId = noteId,
                savedStateHandle = savedStateHandle
            )
        )
        assertTrue(savedStateHandle.get<Boolean>(NOTE_CONTENT_CHANGED_KEY) == true)
    }

    private fun readerViewModel(noteId: NoteId): NoteReaderViewModel = NoteReaderViewModel(
        config = config,
        filesDir = temp.newFolder("files"),
        noteId = noteId,
        loadNoteForReading = LoadNoteForReading(
            NoteRegistryCache(FakeNoteRegistryRepository()),
            FakeNoteContentRepository()
        )
    )

    private fun editorViewModel(noteId: NoteId): NoteEditorViewModel = NoteEditorViewModel(
        config = config,
        filesDir = temp.newFolder("files"),
        noteId = noteId,
        loadEditableNote = LoadEditableNote(
            FakeNoteRegistryRepository(),
            FakeNoteContentRepository()
        ),
        saveNote = SaveNote(
            writeRepository = FakeNoteWriteRepository(),
            registryCache = NoteRegistryCache(FakeNoteRegistryRepository()),
            loadGitStatusSummary = loadGitStatusSummary()
        ),
        loadGitStatusSummary = loadGitStatusSummary()
    )

    private fun loadGitStatusSummary(): LoadGitStatusSummary =
        LoadGitStatusSummary(JGitWorkspaceRepository(), Dispatchers.Main)
}
