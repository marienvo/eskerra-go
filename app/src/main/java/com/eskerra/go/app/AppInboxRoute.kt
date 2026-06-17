package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.feature.inbox.InboxScreen
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.AmbiguousWikiLinkSheet
import java.io.File

@Composable
internal fun AppInboxRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadInboxSummaries: LoadInboxSummariesCached,
    deleteInboxNotes: DeleteInboxNotes,
    loadTodayHub: LoadTodayHub,
    loadTodayHubRow: LoadTodayHubRow,
    activeTodayHubStore: ActiveTodayHubStore,
    todayHubSnapshotStore: TodayHubSnapshotStore,
    workspaceRoot: File?,
    currentRoute: String?,
    entry: NavBackStackEntry,
    navController: NavHostController,
    appSyncViewModel: AppSyncViewModel,
    touchVaultSearchPaths: TouchVaultSearchPaths,
    onInboxUiStateChanged: (InboxUiState) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var ambiguousCandidates by remember { mutableStateOf<List<NoteId>?>(null) }

    val inboxViewModel: InboxViewModel = viewModel(
        key = inboxViewModelKey(currentConfig.remoteUri),
        factory = InboxViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadInboxSummaries = loadInboxSummaries,
            deleteInboxNotes = deleteInboxNotes,
            onInboxMutated = { paths ->
                entry.savedStateHandle[NOTES_CHANGED_KEY] = true
                appSyncViewModel.refreshLocalStatusQuietly()
                scope.touchVaultSearchPathsAsync(
                    touchVaultSearchPaths,
                    currentConfig,
                    filesDir,
                    paths
                )
            }
        )
    )
    val todayHubViewModel: TodayHubViewModel = viewModel(
        key = todayHubViewModelKey(currentConfig.remoteUri),
        factory = TodayHubViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadTodayHub = loadTodayHub,
            loadTodayHubRow = loadTodayHubRow,
            activeTodayHubStore = activeTodayHubStore,
            todayHubSnapshotStore = todayHubSnapshotStore
        )
    )

    val inboxState by inboxViewModel.uiState.collectAsState()
    val selectedNoteIds by inboxViewModel.selectedNoteIds.collectAsState()
    val isDeleting by inboxViewModel.isDeleting.collectAsState()
    val deleteError by inboxViewModel.deleteError.collectAsState()
    val todayHubState by todayHubViewModel.uiState.collectAsState()

    LaunchedEffect(inboxState) {
        onInboxUiStateChanged(inboxState)
    }

    LaunchedEffect(currentRoute) {
        val notesChanged = entry.savedStateHandle.remove<Boolean>(NOTES_CHANGED_KEY) == true
        if (currentRoute == AppRoute.INBOX && notesChanged) {
            inboxViewModel.refresh()
            todayHubViewModel.retry()
            appSyncViewModel.refreshLocalStatusQuietly()
        }
    }

    InboxScreen(
        state = inboxState,
        todayHubState = todayHubState,
        selectedNoteIds = selectedNoteIds,
        isDeleting = isDeleting,
        deleteError = deleteError,
        onRetry = inboxViewModel::refresh,
        onNoteClick = { noteId: NoteId ->
            navController.navigate(AppRoute.note(noteId))
        },
        onAvatarClick = inboxViewModel::toggleSelection,
        onClearSelection = inboxViewModel::clearSelection,
        onDeleteSelected = inboxViewModel::deleteSelected,
        onPreviousWeek = todayHubViewModel::previousWeek,
        onNextWeek = todayHubViewModel::nextWeek,
        onSelectHub = todayHubViewModel::selectHub,
        onRetryTodayHub = todayHubViewModel::retry,
        onOpenInternalNote = { targetId -> navController.navigate(AppRoute.note(targetId)) },
        onOpenExternalUrl = { url -> openExternalUrl(context, url) },
        onAmbiguousWikiLink = { candidates, _ -> ambiguousCandidates = candidates },
        onNoteNotFound = { message -> showNoteNotFoundToast(context, message) },
        onOpenSearch = { navController.navigate(AppRoute.SEARCH) },
        workspaceRoot = workspaceRoot
    )

    val registry = (todayHubState as? TodayHubUiState.Content)?.registry
    if (ambiguousCandidates != null && registry != null) {
        AmbiguousWikiLinkSheet(
            candidates = ambiguousCandidates!!,
            registry = registry,
            onPickNote = { picked ->
                ambiguousCandidates = null
                navController.navigate(AppRoute.note(picked))
            },
            onDismiss = { ambiguousCandidates = null }
        )
    }
}

internal fun inboxViewModelKey(remoteUri: String?): String = "inbox:${remoteUri.orEmpty()}"

internal fun todayHubViewModelKey(remoteUri: String?): String = "today-hub:${remoteUri.orEmpty()}"
