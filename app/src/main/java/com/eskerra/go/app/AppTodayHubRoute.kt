package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.feature.todayhub.TodayHubScreen
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.AmbiguousWikiLinkSheet
import java.io.File

/**
 * Today Hub navigation route (spec §11). Wires [TodayHubViewModel] to the stateless screen and
 * shares the reader's link routing: internal taps push a note, external open the browser, ambiguous
 * wiki links surface the candidate picker.
 */
@Composable
internal fun AppTodayHubRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadTodayHub: LoadTodayHub,
    loadTodayHubRow: LoadTodayHubRow,
    activeTodayHubStore: ActiveTodayHubStore,
    workspaceRoot: File?,
    navController: NavHostController
) {
    val todayHubViewModel: TodayHubViewModel = viewModel(
        key = currentConfig.remoteUri,
        factory = TodayHubViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadTodayHub = loadTodayHub,
            loadTodayHubRow = loadTodayHubRow,
            activeTodayHubStore = activeTodayHubStore
        )
    )
    val todayHubState by todayHubViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var ambiguousCandidates by remember { mutableStateOf<List<NoteId>?>(null) }

    TodayHubScreen(
        state = todayHubState,
        onPreviousWeek = todayHubViewModel::previousWeek,
        onNextWeek = todayHubViewModel::nextWeek,
        onSelectHub = todayHubViewModel::selectHub,
        onRetry = todayHubViewModel::retry,
        onOpenInternalNote = { targetId -> navController.navigate(AppRoute.note(targetId)) },
        onOpenExternalUrl = { url -> openExternalUrl(context, url) },
        onAmbiguousWikiLink = { candidates, _ -> ambiguousCandidates = candidates },
        onNoteNotFound = { message -> showNoteNotFoundToast(context, message) },
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
