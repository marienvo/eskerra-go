package com.eskerra.go.feature.inbox

import androidx.lifecycle.ViewModelStore
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnoozeRepository
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.SnoozeInboxNotes
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeInboxSnapshotStore
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxSnoozeRaceTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun changingNoteflowDoesNotRestoreOldSelectionAfterFailure() = runTest {
        val done = CompletableDeferred<Unit>()
        val note = NoteSummary(NoteId("Inbox/note.md"), "Note", "", true)
        val cache = NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes(note))
        val repository = object : InboxSnoozeRepository {
            override suspend fun snooze(
                config: WorkspaceConfig,
                filesDir: File,
                note: NoteSummary,
                until: Instant
            ): Result<Unit> {
                done.await()
                return Result.failure(IllegalStateException())
            }
        }
        val model = InboxViewModel(
            WorkspaceConfig("Test", "vault", null, "main", 1),
            File("."),
            LoadInboxSummariesCached(LoadInboxSummaries(cache), FakeInboxSnapshotStore()),
            DeleteInboxNotes(
                FakeNoteWriteRepository(),
                cache,
                LoadGitStatusSummary(JGitWorkspaceRepository())
            )
        )
        val store = ViewModelStore()
        store.put("inbox", model)
        model.configureSnooze(
            SnoozeInboxNotes(repository, cache, NoteContentCache(FakeNoteContentRepository()))
        )
        model.toggleSelection(note.id)
        model.snoozeSelected(LocalDate.now().plusDays(1))
        model.setActiveHubFolder("Other")
        done.complete(Unit)
        assertTrue(model.selectedNoteIds.value.isEmpty())
        store.clear()
    }

    @Test
    fun completedWriteRequestsSyncEvenWhenViewModelIsCleared() = runTest {
        val done = CompletableDeferred<Unit>()
        val note = NoteSummary(NoteId("Inbox/note.md"), "Note", "", true)
        val cache = NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes(note))
        val repository = object : InboxSnoozeRepository {
            override suspend fun snooze(
                config: WorkspaceConfig,
                filesDir: File,
                note: NoteSummary,
                until: Instant
            ): Result<Unit> {
                done.await()
                return Result.success(Unit)
            }
        }
        val mutations = mutableListOf<List<String>>()
        val model = InboxViewModel(
            WorkspaceConfig("Test", "vault", null, "main", 1),
            File("."),
            LoadInboxSummariesCached(LoadInboxSummaries(cache), FakeInboxSnapshotStore()),
            DeleteInboxNotes(
                FakeNoteWriteRepository(),
                cache,
                LoadGitStatusSummary(JGitWorkspaceRepository())
            ),
            onInboxMutated = { mutations += it }
        )
        val store = ViewModelStore()
        store.put("inbox", model)
        model.configureSnooze(
            SnoozeInboxNotes(repository, cache, NoteContentCache(FakeNoteContentRepository()))
        )
        model.toggleSelection(note.id)
        model.snoozeSelected(LocalDate.now().plusDays(1))
        store.clear()
        done.complete(Unit)
        assertTrue(mutations == listOf(listOf("Inbox/note.md")))
    }
}
