package com.eskerra.go.feature.note

import androidx.lifecycle.ViewModelStore
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.DatedNoteReadStatusRepository
import com.eskerra.go.core.todayhub.DatedNoteReadStatus
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.SetDatedNoteReadStatus
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.notes.ParsedMarkdownCache
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteReaderDatedReadStatusTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1L
    )
    private val noteId = NoteId("Work/Journal/2026-11-22.md")

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @Test
    fun successfulTogglePublishesNewDocumentAndRequestsSync() = runTest {
        val repository = FakeDatedRepository(Result.success("# Today ✅\n\nBody ✅"))
        val viewModel = createViewModel(repository, "# Today\n\nBody ✅")
        var persisted = 0

        viewModel.toggleDatedNoteReadStatus { persisted++ }

        val state = viewModel.uiState.value as NoteReaderUiState.Content
        assertEquals("Today ✅", state.title)
        assertEquals("# Today ✅\n\nBody ✅", state.document.content.markdown)
        assertEquals("Today ✅", state.document.registry.notes.single().title)
        assertFalse(state.readStatusMutationInProgress)
        assertNull(state.readStatusMessage)
        assertEquals(1, persisted)
    }

    @Test
    fun readNoteCanBecomeUnread() = runTest {
        val repository = FakeDatedRepository(Result.success("# Today\n\nBody"))
        val viewModel = createViewModel(repository, "# Today ✅\n\nBody")

        viewModel.toggleDatedNoteReadStatus()

        val state = viewModel.uiState.value as NoteReaderUiState.Content
        assertEquals("Today", state.title)
        assertFalse(DatedNoteReadStatus.isRead(state.document.content.markdown))
    }

    @Test
    fun failedWriteKeepsPreviousStateAndDoesNotRequestSync() = runTest {
        val repository = FakeDatedRepository(Result.failure(Exception("write failed")))
        val viewModel = createViewModel(repository, "# Today\n\nBody")
        var persisted = 0

        viewModel.toggleDatedNoteReadStatus { persisted++ }

        val state = viewModel.uiState.value as NoteReaderUiState.Content
        assertEquals("Today", state.title)
        assertEquals("# Today\n\nBody", state.document.content.markdown)
        assertFalse(state.readStatusMutationInProgress)
        assertEquals(NoteReaderViewModel.READ_STATUS_WRITE_ERROR, state.readStatusMessage)
        assertEquals(0, persisted)
    }

    @Test
    fun doubleTapWhileWritingRunsOnlyOneMutation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeDatedRepository(
            Result.success("# Today ✅\n\nBody"),
            delayMs = 1_000L
        )
        val viewModel = createViewModel(repository, "# Today\n\nBody")
        advanceUntilIdle()

        viewModel.toggleDatedNoteReadStatus()
        viewModel.toggleDatedNoteReadStatus()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    @Test
    fun successfulToggleReparsesLatestExternalBody() = runTest {
        val parsedBodies = mutableListOf<String>()
        val parsedCache = ParsedMarkdownCache(
            prepare = { body ->
                parsedBodies += body
                PreparedMarkdown(emptyList())
            }
        )
        val repository = FakeDatedRepository(Result.success("# Today ✅\n\nNew external body"))
        val viewModel = createViewModel(
            repository,
            "# Today\n\nOld body",
            parsedMarkdownCache = parsedCache
        )

        viewModel.toggleDatedNoteReadStatus()

        val state = viewModel.uiState.value as NoteReaderUiState.Content
        assertEquals("New external body", state.bodyMarkdown)
        assertEquals("New external body", parsedBodies.last())
    }

    @Test
    fun readerClearAfterWriteStillPublishesPersistedCallback() = runTest {
        val operationScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val repository = FakeDatedRepository(
            Result.success("# Today ✅\n\nBody"),
            onWrite = { store.clear() }
        )
        val viewModel = createViewModel(
            repository,
            "# Today\n\nBody",
            operationScope = operationScope
        )
        store.put("reader", viewModel)
        var persisted = 0

        viewModel.toggleDatedNoteReadStatus { persisted++ }
        advanceUntilIdle()

        assertEquals(1, persisted)
    }

    private fun createViewModel(
        repository: FakeDatedRepository,
        markdown: String,
        parsedMarkdownCache: ParsedMarkdownCache = ParsedMarkdownCache(
            prepare = { PreparedMarkdown(emptyList()) }
        ),
        operationScope: CoroutineScope? = null
    ): NoteReaderViewModel {
        val registryCache = NoteRegistryCache(
            FakeNoteRegistryRepository.withInboxNotes(
                NoteSummary(noteId, "Today", "", isInbox = false)
            )
        )
        return NoteReaderViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            noteId = noteId,
            loadNoteForReading = LoadNoteForReading(
                registryCache,
                FakeNoteContentRepository.withContent(noteId, markdown)
            ),
            parsedMarkdownCache = parsedMarkdownCache,
            setDatedNoteReadStatus = SetDatedNoteReadStatus(repository, registryCache),
            operationScope = operationScope
        )
    }

    private class FakeDatedRepository(
        private val result: Result<String>,
        private val delayMs: Long = 0L,
        private val onWrite: () -> Unit = {}
    ) : DatedNoteReadStatusRepository {
        var calls = 0
            private set

        override suspend fun setRead(
            config: WorkspaceConfig,
            filesDir: File,
            notePath: NotePath,
            read: Boolean
        ): Result<String> {
            calls++
            if (delayMs > 0L) delay(delayMs)
            onWrite()
            return result
        }
    }
}
