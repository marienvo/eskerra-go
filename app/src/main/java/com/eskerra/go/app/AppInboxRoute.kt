package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.feature.inbox.InboxScreen
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File

@Composable
internal fun AppInboxRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadInboxSummaries: LoadInboxSummariesCached,
    currentRoute: String?,
    entry: NavBackStackEntry,
    navController: NavHostController,
    appSyncViewModel: AppSyncViewModel,
    onInboxUiStateChanged: (InboxUiState) -> Unit
) {
    val inboxViewModel: InboxViewModel = viewModel(
        key = currentConfig.remoteUri,
        factory = InboxViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadInboxSummaries = loadInboxSummaries
        )
    )
    val inboxState by inboxViewModel.uiState.collectAsState()
    val showRefreshIndicator by inboxViewModel.showRefreshIndicator.collectAsState()

    LaunchedEffect(inboxState) {
        onInboxUiStateChanged(inboxState)
    }

    LaunchedEffect(currentRoute) {
        val notesChanged = entry.savedStateHandle.remove<Boolean>(NOTES_CHANGED_KEY) == true
        if (currentRoute == AppRoute.INBOX && notesChanged) {
            inboxViewModel.refresh()
            appSyncViewModel.refreshLocalStatusQuietly()
        }
    }

    InboxScreen(
        state = inboxState,
        showRefreshIndicator = showRefreshIndicator,
        onRetry = inboxViewModel::refresh,
        onNoteClick = { noteId: NoteId ->
            navController.navigate(AppRoute.note(noteId))
        }
    )
}
