package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteRegistrySnapshotStore
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.todayhub.FakeTodayHubSnapshotStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TodayHubNotificationSelectionTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig("vault", "vault", null, "main", 1)
    private val workHub = NoteId("Work/Today.md")
    private val homeHub = NoteId("Home/Today.md")
    private val workDated = NoteId("Work/Daily/2026-11-01.md")
    private val homeDated = NoteId("Home/Daily/2026-11-01.md")

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun notificationSelectionBeforeDeferredRefreshWins() = runTest {
        val store = FakeActiveTodayHubStore()
        val vm = viewModel(store = store)

        vm.selectHubFromNotification(workHub)
        vm.startDeferredRefresh()
        advanceUntilIdle()

        assertSelected(vm, store, workHub, workDated)
    }

    @Test
    fun notificationSelectionCancelsDelayedDefaultRefresh() = runTest {
        val store = FakeActiveTodayHubStore()
        val vm = viewModel(store = store, refreshDelayMs = 1_000L)

        vm.startDeferredRefresh()
        vm.selectHubFromNotification(workHub)
        advanceUntilIdle()

        assertSelected(vm, store, workHub, workDated)
    }

    private fun assertSelected(
        vm: TodayHubViewModel,
        store: FakeActiveTodayHubStore,
        hub: NoteId,
        dated: NoteId
    ) {
        val state = vm.uiState.value as TodayHubUiState.Content
        assertEquals(hub, state.activeHubId)
        assertEquals(listOf(dated), state.datedLinks.map { it.noteId })
        assertEquals(hub.value, store.value)
    }

    private fun viewModel(
        store: FakeActiveTodayHubStore,
        refreshDelayMs: Long = 0L
    ): TodayHubViewModel {
        val filesDir = temp.newFolder("files")
        File(filesDir, config.relativePath).mkdirs()
        val registry = NoteRegistry.fromNotes(
            listOf(workHub, homeHub, workDated, homeDated).map {
                NoteSummary(it, it.value.substringBefore('/'), "", false)
            }
        )
        val notes = FakeNoteRegistryRepository(
            result = Result.success(registry),
            refreshDelayMs = refreshDelayMs
        )
        return TodayHubViewModel(
            config = config,
            filesDir = filesDir,
            loadTodayHub = LoadTodayHub(
                NoteRegistryCache(notes, FakeNoteRegistrySnapshotStore()),
                TestContentRepository(),
                fakeNoteflowRepository(notes),
                today = { LocalDate(2026, 11, 2) }
            ),
            loadTodayHubRow = LoadTodayHubRow(TestContentRepository()),
            activeTodayHubStore = store,
            todayHubSnapshotStore = FakeTodayHubSnapshotStore(),
            today = { LocalDate(2026, 11, 2) }
        )
    }

    private class TestContentRepository : NoteContentRepository {
        override suspend fun load(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId
        ): Result<NoteContent> = Result.success(
            NoteContent(
                noteId,
                NotePath.fromRelativePath(noteId.value).getOrThrow(),
                "# ${noteId.value}"
            )
        )
    }

    private class FakeActiveTodayHubStore : ActiveTodayHubStore {
        var value: String? = null
        override suspend fun read(): String? = value
        override suspend fun save(noteId: String) {
            value = noteId
        }
    }
}
