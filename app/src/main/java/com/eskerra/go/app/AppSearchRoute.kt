package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.feature.search.SearchScreen
import java.io.File

@Composable
internal fun AppSearchRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    searchVault: SearchVault,
    maintainVaultSearchIndex: MaintainVaultSearchIndex,
    repairVaultSearchIndex: RepairVaultSearchIndex,
    navController: NavHostController
) {
    val searchViewModel: SearchViewModel = viewModel(
        key = currentConfig.remoteUri,
        factory = SearchViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            searchVault = searchVault,
            maintainVaultSearchIndex = maintainVaultSearchIndex,
            repairVaultSearchIndex = repairVaultSearchIndex
        )
    )
    val query by searchViewModel.query.collectAsState()
    val state by searchViewModel.uiState.collectAsState()

    SearchScreen(
        state = state,
        query = query,
        onQueryChange = searchViewModel::onQueryChange,
        onBack = { navController.popBackStack() },
        onOpenNote = { noteId: NoteId -> navController.navigate(AppRoute.note(noteId)) },
        onRetryIndex = searchViewModel::retryIndex
    )
}
