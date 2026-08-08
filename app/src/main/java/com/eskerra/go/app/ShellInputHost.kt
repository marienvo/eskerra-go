package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.feature.sync.AppSyncViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * Assembles the bottom pill's state: the inbox composer, the search input it is multiplexed with,
 * and the share intake that can seed the draft from another app.
 *
 * Owning all three here keeps navigation in one place — a share must land where the pill is
 * actually visible, and only this layer knows both the current route and the nav controller.
 */
@Composable
internal fun rememberShellInput(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    createInboxNote: CreateInboxNote,
    activeTodayHubStore: ActiveTodayHubStore,
    touchVaultSearchPaths: TouchVaultSearchPaths,
    appSyncViewModel: AppSyncViewModel,
    scope: CoroutineScope,
    currentRoute: String?,
    selectedTopLevelRoute: String?,
    markInboxNotesChanged: () -> Unit,
    searchVault: SearchVault,
    maintainVaultSearchIndex: MaintainVaultSearchIndex,
    repairVaultSearchIndex: RepairVaultSearchIndex,
    navController: NavHostController,
    shareIntake: ShareIntake
): AppShellInputState {
    val newNoteInputState = rememberShellNewNoteInputState(
        currentConfig = currentConfig,
        filesDir = filesDir,
        createInboxNote = createInboxNote,
        activeTodayHubStore = activeTodayHubStore,
        touchVaultSearchPaths = touchVaultSearchPaths,
        appSyncViewModel = appSyncViewModel,
        scope = scope,
        currentRoute = currentRoute,
        selectedTopLevelRoute = selectedTopLevelRoute,
        markInboxNotesChanged = markInboxNotesChanged,
        shareIntake = shareIntake,
        onSharePrefillApplied = {
            when (shareNavAction(currentRoute, selectedTopLevelRoute)) {
                ShareNavAction.NoOp -> Unit
                ShareNavAction.PopSearch -> navController.popBackStack()
                ShareNavAction.SwitchToHome -> navController.navigate(AppRoute.HOME_GRAPH) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                }
            }
        }
    )
    return rememberAppShellInputState(
        currentConfig = currentConfig,
        filesDir = filesDir,
        searchVault = searchVault,
        maintainVaultSearchIndex = maintainVaultSearchIndex,
        repairVaultSearchIndex = repairVaultSearchIndex,
        navController = navController,
        currentRoute = currentRoute,
        newNoteInputState = newNoteInputState
    )
}
