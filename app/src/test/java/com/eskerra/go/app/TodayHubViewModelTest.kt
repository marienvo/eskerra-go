package com.eskerra.go.app

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteRegistrySnapshotStore
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.todayhub.FakeTodayHubSnapshotStore
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.todayhub.TodayHubUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TodayHubViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    /** Wednesday → Monday week stem 2026-04-06. */
    private val fixedToday = { LocalDate(2026, 4, 8) }

    private val hubMarkdown = """
        ---
        perpetualType: weekly
        columns:
          - Tasks
        start: monday
        ---
        # Daily hub

        Intro body here.
    """.trimIndent()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun note(path: String): NoteSummary =
        NoteSummary(id = NoteId(path), title = path, snippet = "", isInbox = false)

    private fun viewModel(
        content: NoteContentRepository,
        registry: FakeNoteRegistryRepository,
        store: ActiveTodayHubStore = FakeActiveTodayHubStore(),
        filesDir: File = temp.newFolder("files"),
        snapshotStore: FakeTodayHubSnapshotStore = FakeTodayHubSnapshotStore(),
        cachedRegistry: com.eskerra.go.core.model.NoteRegistry? = null
    ): TodayHubViewModel = TodayHubViewModel(
        config = config,
        filesDir = filesDir,
        loadTodayHub = LoadTodayHub(
            NoteRegistryCache(registry, FakeNoteRegistrySnapshotStore(cachedRegistry)),
            content
        ),
        loadTodayHubRow = LoadTodayHubRow(content),
        activeTodayHubStore = store,
        todayHubSnapshotStore = snapshotStore,
        today = fixedToday
    )

    @Test
    fun emptyWhenNoHubs() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(note("Inbox/a.md"))
        val vm = viewModel(MapContentRepository(), registry)
        advanceUntilIdle()
        assertEquals(TodayHubUiState.Empty, vm.uiState.value)
    }

    @Test
    fun contentExposesHeadersWeekAndIntro() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            note("Daily/Today.md"),
            note("Daily/2026-03-30.md"),
            note("Daily/2026-04-06.md")
        )
        val content = MapContentRepository(
            NoteId("Daily/Today.md") to hubMarkdown,
            NoteId("Daily/2026-04-06.md") to "default\n\n::today-section::\n\ntasks"
        )
        val vm = viewModel(content, registry)
        advanceUntilIdle()

        val state = vm.uiState.value as TodayHubUiState.Content
        assertEquals("Daily", state.folderLabel)
        assertEquals("2026-04-06", state.selectedWeekStem)
        assertEquals(listOf("Monday, April 6, 2026", "Tasks"), state.columnHeaders)
        assertTrue(state.introMarkdown.contains("Intro body here."))
        assertTrue(state.canGoPrev)
        assertFalse(state.canGoNext)
        assertFalse(state.rowLoading)
        assertEquals(listOf("default", "tasks"), state.row?.columns)
    }

    @Test
    fun init_withSnapshotEmitsContentBeforeRevalidation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val registry = FakeNoteRegistryRepository(
            result = Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(
                    listOf(note("Daily/Today.md"), note("Daily/2026-04-06.md"))
                )
            ),
            refreshDelayMs = 1_000L
        )
        val cachedRegistry = com.eskerra.go.core.model.NoteRegistry.fromNotes(
            listOf(note("Daily/Today.md"), note("Daily/2026-04-06.md"))
        )
        val snapshotStore = FakeTodayHubSnapshotStore()
        snapshotStore.save(config, filesDir, snapshot(rowBody = "cached row"))

        val vm = viewModel(
            content = MapContentRepository(
                NoteId("Daily/Today.md") to hubMarkdown,
                NoteId("Daily/2026-04-06.md") to "cached row"
            ),
            registry = registry,
            filesDir = filesDir,
            snapshotStore = snapshotStore,
            cachedRegistry = cachedRegistry
        )
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value as TodayHubUiState.Content
        assertEquals("Daily", state.folderLabel)
        assertEquals(listOf("cached row", ""), state.row?.columns)
        assertFalse(state.rowLoading)
    }

    @Test
    fun revalidation_withUnchangedSnapshotDoesNotReplaceContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val notes = listOf(note("Daily/Today.md"), note("Daily/2026-04-06.md"))
        val cachedRegistry = com.eskerra.go.core.model.NoteRegistry.fromNotes(notes)
        val registry = FakeNoteRegistryRepository(
            result = Result.success(com.eskerra.go.core.model.NoteRegistry.fromNotes(notes)),
            refreshDelayMs = 1_000L
        )
        val snapshotStore = FakeTodayHubSnapshotStore()
        snapshotStore.save(config, filesDir, snapshot(rowBody = "stable row"))
        val vm = viewModel(
            content = MapContentRepository(
                NoteId("Daily/Today.md") to hubMarkdown,
                NoteId("Daily/2026-04-06.md") to "stable row"
            ),
            registry = registry,
            filesDir = filesDir,
            snapshotStore = snapshotStore,
            cachedRegistry = cachedRegistry
        )
        dispatcher.scheduler.runCurrent()
        val initial = vm.uiState.value as TodayHubUiState.Content

        advanceUntilIdle()

        assertSame(initial, vm.uiState.value)
    }

    @Test
    fun revalidation_withChangedRowSwapsContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val notes = listOf(note("Daily/Today.md"), note("Daily/2026-04-06.md"))
        val cachedRegistry = com.eskerra.go.core.model.NoteRegistry.fromNotes(notes)
        val registry = FakeNoteRegistryRepository(
            result = Result.success(com.eskerra.go.core.model.NoteRegistry.fromNotes(notes)),
            refreshDelayMs = 1_000L
        )
        val snapshotStore = FakeTodayHubSnapshotStore()
        snapshotStore.save(config, filesDir, snapshot(rowBody = "stale row"))
        val vm = viewModel(
            content = MapContentRepository(
                NoteId("Daily/Today.md") to hubMarkdown,
                NoteId("Daily/2026-04-06.md") to "fresh row"
            ),
            registry = registry,
            filesDir = filesDir,
            snapshotStore = snapshotStore,
            cachedRegistry = cachedRegistry
        )
        dispatcher.scheduler.runCurrent()
        assertEquals(
            listOf("stale row", ""),
            (vm.uiState.value as TodayHubUiState.Content).row?.columns
        )

        advanceUntilIdle()

        assertEquals(
            listOf("fresh row", ""),
            (vm.uiState.value as TodayHubUiState.Content).row?.columns
        )
    }

    @Test
    fun previousWeekNavigatesToEarlierStem() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            note("Daily/Today.md"),
            note("Daily/2026-03-30.md"),
            note("Daily/2026-04-06.md")
        )
        val content = MapContentRepository(NoteId("Daily/Today.md") to hubMarkdown)
        val vm = viewModel(content, registry)
        advanceUntilIdle()

        vm.previousWeek()
        advanceUntilIdle()

        val state = vm.uiState.value as TodayHubUiState.Content
        assertEquals("2026-03-30", state.selectedWeekStem)
        assertFalse(state.canGoPrev)
        assertTrue(state.canGoNext)
    }

    @Test
    fun persistsActiveHubId() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(note("Daily/Today.md"))
        val store = FakeActiveTodayHubStore()
        val vm =
            viewModel(
                MapContentRepository(NoteId("Daily/Today.md") to hubMarkdown),
                registry,
                store
            )
        advanceUntilIdle()

        assertTrue(vm.uiState.value is TodayHubUiState.Content)
        assertEquals("Daily/Today.md", store.read())
    }

    @Test
    fun errorWhenHubReadFails() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(note("Daily/Today.md"))
        val content = MapContentRepository().apply {
            failWith(NoteContentError.ReadFailed("io"))
        }
        val vm = viewModel(content, registry)
        advanceUntilIdle()

        assertEquals(
            TodayHubUiState.Error(TodayHubViewModel.READ_ERROR_MESSAGE),
            vm.uiState.value
        )
    }

    private class MapContentRepository(vararg entries: Pair<NoteId, String>) :
        NoteContentRepository {
        private val byId = entries.toMap()
        private var failure: NoteContentError? = null

        fun failWith(error: NoteContentError) {
            failure = error
        }

        override suspend fun load(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId
        ): Result<NoteContent> {
            failure?.let { return Result.failure(NoteContentException(it)) }
            val markdown = byId[noteId]
                ?: return Result.failure(NoteContentException(NoteContentError.NotFound))
            val path = NotePath.fromRelativePath(noteId.value).getOrThrow()
            return Result.success(NoteContent(noteId, path, markdown))
        }
    }

    private class FakeActiveTodayHubStore : ActiveTodayHubStore {
        private var stored: String? = null
        override suspend fun read(): String? = stored
        override suspend fun save(noteId: String) {
            stored = noteId
        }
    }

    private fun snapshot(rowBody: String): TodayHubSnapshot = TodayHubSnapshot(
        hubs = listOf(TodayHubRef(NoteId("Daily/Today.md"), "Daily")),
        activeHubId = NoteId("Daily/Today.md"),
        settings = TodayHubFrontmatter.Settings(
            columns = listOf("Tasks"),
            start = TodayHubFrontmatter.StartDay.MONDAY
        ),
        introMarkdown = "# Daily hub\n\nIntro body here.",
        availableWeekStems = listOf("2026-04-06"),
        selectedWeekStem = "2026-04-06",
        row = TodayHubRow(
            rowNoteId = NoteId("Daily/2026-04-06.md"),
            weekStartStem = "2026-04-06",
            columns = listOf(rowBody, "")
        )
    )

    private fun prepareWorkspace(filesDir: File) {
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
    }
}
