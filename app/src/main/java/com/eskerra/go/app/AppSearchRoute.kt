package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.WarmNote
import com.eskerra.go.feature.search.SearchScreen
import com.eskerra.go.feature.search.SearchViewModel
import java.io.File

@Composable
internal fun AppSearchRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    warmNote: WarmNote,
    searchViewModel: SearchViewModel,
    navController: NavHostController,
    entry: NavBackStackEntry
) {
    val scope = rememberCoroutineScope()
    val routeQuery = remember(entry) {
        entry.arguments
            ?.getString(AppRoute.SEARCH_QUERY_ARG)
            ?.let(AppRoute::decodeSearchQuery)
            .orEmpty()
    }
    LaunchedEffect(routeQuery) {
        if (routeQuery.isNotBlank()) {
            searchViewModel.applyRouteQuery(routeQuery)
        }
    }

    val query by searchViewModel.query.collectAsState()
    val state by searchViewModel.uiState.collectAsState()

    SearchScreen(
        state = state,
        query = query,
        onOpenNote = { noteId: NoteId ->
            scope.openNoteWithWarmBudget(warmNote, currentConfig, filesDir, navController, noteId)
        },
        onRetryIndex = searchViewModel::retryIndex
    )
}
