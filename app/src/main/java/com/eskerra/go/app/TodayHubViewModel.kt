package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.todayhub.TodayHubData
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubLabels
import com.eskerra.go.core.todayhub.TodayHubNavigation
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import com.eskerra.go.core.todayhub.TodayHubWeeks
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.feature.todayhub.TodayHubUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Drives the Today Hub screen (spec §11): discovers hubs, loads the active hub intro + settings,
 * tracks the selected week, and fetches week rows. Week math and navigation live in `core/todayhub`;
 * this view model only orchestrates and persists the active hub id.
 */
class TodayHubViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadTodayHub: LoadTodayHub,
    private val loadTodayHubRow: LoadTodayHubRow,
    private val activeTodayHubStore: ActiveTodayHubStore,
    private val todayHubSnapshotStore: TodayHubSnapshotStore,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayHubUiState>(TodayHubUiState.Loading)
    val uiState: StateFlow<TodayHubUiState> = _uiState.asStateFlow()

    private var loaded: Loaded? = null
    private var loadJob: Job? = null
    private var rowJob: Job? = null

    /** Resolved hub context plus the navigation window derived from it. */
    private data class Loaded(
        val data: TodayHubData,
        val navigableStems: List<String>,
        val selectedStem: String
    )

    init {
        load(preferredHubId = null, restoreFromStore = true, restoreSnapshotFirst = true)
    }

    fun retry() =
        load(preferredHubId = loaded?.data?.activeHubId, restoreFromStore = loaded == null)

    fun selectHub(noteId: NoteId) {
        if (noteId == loaded?.data?.activeHubId) return
        load(preferredHubId = noteId, restoreFromStore = false)
    }

    fun previousWeek() = stepWeek(-1)

    fun nextWeek() = stepWeek(1)

    /**
     * Resets the selected week back to the current week. Used when the user re-taps Home while
     * already on the inbox. Returns `true` when it actually changed the week, so the caller can
     * scroll the list to top only then; `false` when no hub is loaded or already on this week.
     */
    fun resetToCurrentWeek(): Boolean {
        val current = loaded ?: return false
        val currentStem = TodayHubNavigation.currentWeekStem(today(), current.data.settings.start)
        if (current.selectedStem == currentStem) return false
        loaded = current.copy(selectedStem = currentStem)
        emitContent(rowLoading = false, row = null)
        loadRow(current.data, currentStem)
        return true
    }

    private fun stepWeek(delta: Int) {
        val current = loaded ?: return
        val target = TodayHubNavigation.adjacentStem(
            current.navigableStems,
            current.selectedStem,
            delta
        ) ?: return
        loaded = current.copy(selectedStem = target)
        emitContent(rowLoading = false, row = null)
        loadRow(current.data, target)
    }

    private fun load(
        preferredHubId: NoteId?,
        restoreFromStore: Boolean,
        restoreSnapshotFirst: Boolean = false
    ) {
        loadJob?.cancel()
        rowJob?.cancel()
        loadJob = viewModelScope.launch {
            if (restoreSnapshotFirst) {
                restoreSnapshot()
            }
            if (_uiState.value !is TodayHubUiState.Content) {
                _uiState.value = TodayHubUiState.Loading
            }
            val preferred = preferredHubId
                ?: if (restoreFromStore) activeTodayHubStore.read()?.let { NoteId(it) } else null

            loadTodayHub(config, filesDir, preferred).fold(
                onSuccess = { data ->
                    if (data == null) {
                        loaded = null
                        _uiState.value = TodayHubUiState.Empty
                    } else {
                        onHubLoaded(data)
                    }
                },
                onFailure = { error -> _uiState.value = TodayHubUiState.Error(messageFor(error)) }
            )
        }
    }

    private fun onHubLoaded(data: TodayHubData) {
        activeTodayHubStore.persist(data.activeHubId)
        val currentStem = TodayHubNavigation.currentWeekStem(today(), data.settings.start)
        val navigable = TodayHubNavigation.navigableWeekStems(data.availableWeekStems, currentStem)
        val retainedRow = (_uiState.value as? TodayHubUiState.Content)
            ?.takeIf { it.activeHubId == data.activeHubId && it.selectedWeekStem == currentStem }
            ?.row
        loaded = Loaded(data = data, navigableStems = navigable, selectedStem = currentStem)
        emitContent(rowLoading = false, row = retainedRow)
        loadRow(data, currentStem, showRowLoading = false)
    }

    private fun ActiveTodayHubStore.persist(noteId: NoteId) {
        viewModelScope.launch { save(noteId.value) }
    }

    private fun loadRow(data: TodayHubData, stem: String, showRowLoading: Boolean = true) {
        rowJob?.cancel()
        rowJob = viewModelScope.launch {
            val spinner = if (showRowLoading) {
                launch {
                    delay(ROW_NAV_LOADING_DELAY_MS)
                    emitContent(rowLoading = true, row = currentRow())
                }
            } else {
                null
            }
            val result = loadTodayHubRow(
                config = config,
                filesDir = filesDir,
                todayNoteId = data.activeHubId,
                weekStartStem = stem,
                columnCount = data.columnCount
            )
            spinner?.cancel()
            result.fold(
                onSuccess = { row ->
                    if (loaded?.selectedStem == stem) {
                        emitContent(rowLoading = false, row = row)
                        saveSnapshotIfCurrentWeek()
                    }
                },
                onFailure = { error ->
                    if (loaded?.selectedStem == stem) {
                        _uiState.value = TodayHubUiState.Error(messageFor(error))
                    }
                }
            )
        }
    }

    private fun currentRow(): TodayHubRow? = (_uiState.value as? TodayHubUiState.Content)?.row

    private fun emitContent(rowLoading: Boolean, row: TodayHubRow?) {
        val content = buildContent(rowLoading = rowLoading, row = row) ?: return
        if (_uiState.value != content) {
            _uiState.value = content
        }
    }

    private fun buildContent(rowLoading: Boolean, row: TodayHubRow?): TodayHubUiState.Content? {
        val current = loaded ?: return null
        val data = current.data
        val weekStart = TodayHubWeeks.parseRowStem(current.selectedStem) ?: return null
        val progress = TodayHubWeeks.weekProgress(weekStart, today())
        return TodayHubUiState.Content(
            hubs = data.hubs,
            activeHubId = data.activeHubId,
            folderLabel = data.folderLabel,
            introMarkdown = data.introMarkdown,
            registry = data.registry,
            columnHeaders = listOf(TodayHubLabels.weekDateLong(weekStart)) + data.settings.columns,
            selectedWeekStem = current.selectedStem,
            weekRangeLabel = TodayHubLabels.weekRangeShort(weekStart),
            canGoPrev = TodayHubNavigation.hasPrev(current.navigableStems, current.selectedStem),
            canGoNext = TodayHubNavigation.hasNext(current.navigableStems, current.selectedStem),
            progressSegments = TodayHubWeeks.weekProgressSegments(
                progress = progress,
                weekStart = weekStart,
                now = today(),
                cellPx = PROGRESS_CELL_WEIGHT,
                gapPx = 0
            ),
            row = row,
            rowLoading = rowLoading
        )
    }

    private suspend fun restoreSnapshot() {
        val snapshot = todayHubSnapshotStore.read(config, filesDir) ?: return
        val registry = loadTodayHub.currentRegistry(config, filesDir) ?: return
        applySnapshot(snapshot, registry)
    }

    private fun applySnapshot(snapshot: TodayHubSnapshot, registry: NoteRegistry) {
        val data = TodayHubData(
            hubs = snapshot.hubs,
            activeHubId = snapshot.activeHubId,
            settings = snapshot.settings,
            introMarkdown = snapshot.introMarkdown,
            availableWeekStems = snapshot.availableWeekStems,
            registry = registry
        )
        val currentStem = TodayHubNavigation.currentWeekStem(today(), snapshot.settings.start)
        val navigable = TodayHubNavigation.navigableWeekStems(
            snapshot.availableWeekStems,
            currentStem
        )
        loaded = Loaded(data = data, navigableStems = navigable, selectedStem = currentStem)
        emitContent(
            rowLoading = false,
            row = snapshot.row?.takeIf { it.weekStartStem == currentStem }
        )
    }

    private fun saveSnapshotIfCurrentWeek() {
        val current = loaded ?: return
        val content = _uiState.value as? TodayHubUiState.Content ?: return
        val currentStem = TodayHubNavigation.currentWeekStem(today(), current.data.settings.start)
        if (content.selectedWeekStem != currentStem) {
            return
        }
        viewModelScope.launch {
            todayHubSnapshotStore.save(
                config = config,
                filesDir = filesDir,
                snapshot = TodayHubSnapshot(
                    hubs = content.hubs,
                    activeHubId = content.activeHubId,
                    settings = TodayHubFrontmatter.Settings(
                        perpetualType = current.data.settings.perpetualType,
                        columns = current.data.settings.columns,
                        start = current.data.settings.start
                    ),
                    introMarkdown = content.introMarkdown,
                    availableWeekStems = current.data.availableWeekStems,
                    selectedWeekStem = content.selectedWeekStem,
                    row = content.row
                )
            )
        }
    }

    private fun messageFor(error: Throwable): String {
        val contentError = (error as? NoteContentException)?.error
        return when (contentError) {
            NoteContentError.InvalidWorkspacePath -> WORKSPACE_UNAVAILABLE_MESSAGE
            NoteContentError.WorkspaceMissing -> WORKSPACE_MISSING_MESSAGE
            NoteContentError.NotFound,
            NoteContentError.InvalidNoteId,
            is NoteContentError.ReadFailed,
            null -> READ_ERROR_MESSAGE
        }
    }

    companion object {
        const val ROW_NAV_LOADING_DELAY_MS = 200L
        const val READ_ERROR_MESSAGE = "Could not open this hub."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."

        /** Each normal day occupies one progress segment of weight; the merged weekend doubles it. */
        private const val PROGRESS_CELL_WEIGHT = 1

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadTodayHub: LoadTodayHub,
            loadTodayHubRow: LoadTodayHubRow,
            activeTodayHubStore: ActiveTodayHubStore,
            todayHubSnapshotStore: TodayHubSnapshotStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayHubViewModel(
                config = config,
                filesDir = filesDir,
                loadTodayHub = loadTodayHub,
                loadTodayHubRow = loadTodayHubRow,
                activeTodayHubStore = activeTodayHubStore,
                todayHubSnapshotStore = todayHubSnapshotStore
            ) as T
        }
    }
}
