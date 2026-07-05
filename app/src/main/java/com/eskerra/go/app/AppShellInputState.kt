package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import java.io.File

data class ShellInputPresentation(
    val visible: Boolean,
    val searchMode: Boolean,
    val value: String,
    val onSearchModeChange: (Boolean) -> Unit,
    val onValueChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val submitEnabled: Boolean,
    val isSaving: Boolean,
    val errorMessage: String?
)

internal data class AppShellInputState(
    val presentation: ShellInputPresentation,
    val searchViewModel: SearchViewModel
)

@Composable
internal fun rememberAppShellInputState(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    searchVault: SearchVault,
    maintainVaultSearchIndex: MaintainVaultSearchIndex,
    repairVaultSearchIndex: RepairVaultSearchIndex,
    navController: NavHostController,
    currentRoute: String?,
    newNoteInputState: ShellNewNoteInputState
): AppShellInputState {
    val searchViewModel: SearchViewModel = viewModel(
        key = "search:${currentConfig.remoteUri.orEmpty()}",
        factory = SearchViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            searchVault = searchVault,
            maintainVaultSearchIndex = maintainVaultSearchIndex,
            repairVaultSearchIndex = repairVaultSearchIndex
        )
    )
    val searchQuery by searchViewModel.query.collectAsState()
    var searchMode by rememberSaveable { mutableStateOf(false) }

    val presentation = buildShellInputPresentation(
        searchMode = searchMode,
        searchQuery = searchQuery,
        newNoteInputState = newNoteInputState,
        onSearchModeChange = { enabled ->
            searchMode = enabled
            if (enabled) {
                navController.navigate(AppRoute.SEARCH) { launchSingleTop = true }
            } else if (currentRoute == AppRoute.SEARCH_PATTERN) {
                navController.popBackStack()
            }
        },
        onSearchQueryChange = searchViewModel::onQueryChange,
        onSearchSubmit = {
            navController.navigate(AppRoute.SEARCH) { launchSingleTop = true }
        }
    )
    return AppShellInputState(presentation, searchViewModel)
}

internal fun buildShellInputPresentation(
    searchMode: Boolean,
    searchQuery: String,
    newNoteInputState: ShellNewNoteInputState,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit
): ShellInputPresentation = if (searchMode) {
    ShellInputPresentation(
        visible = newNoteInputState.visible,
        searchMode = true,
        value = searchQuery,
        onSearchModeChange = onSearchModeChange,
        onValueChange = onSearchQueryChange,
        onSubmit = onSearchSubmit,
        submitEnabled = searchQuery.isNotBlank(),
        isSaving = false,
        errorMessage = null
    )
} else {
    ShellInputPresentation(
        visible = newNoteInputState.visible,
        searchMode = false,
        value = newNoteInputState.draft,
        onSearchModeChange = onSearchModeChange,
        onValueChange = newNoteInputState.onDraftChange,
        onSubmit = newNoteInputState.onSave,
        submitEnabled = newNoteInputState.canSave && !newNoteInputState.isSaving,
        isSaving = newNoteInputState.isSaving,
        errorMessage = newNoteInputState.errorMessage
    )
}
