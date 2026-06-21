package com.eskerra.go.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.eskerra.go.core.model.hasSyncWork
import com.eskerra.go.feature.sync.SyncUiState

/**
 * Shell navigation semantics: what a bottom-nav / shell tab tap does, given the current route.
 * Extracted from [App] to keep that file within budget and so the branching — which has several
 * restore/reset edge cases — can be unit-tested without a live NavController.
 *
 * The home (inbox) destination is the NavHost start destination, so it is always at the root of the
 * back stack. Top-level tabs use the multiple-back-stack save/restore pattern so a stack survives a
 * round trip. Home behaves progressively:
 *
 * - Re-tapping a non-home tab that is already current is a no-op.
 * - Home from a note/editor (a drill-down from home) pops back to the inbox; the Today Hub keeps the
 *   week it was on (no reset).
 * - Home from another top-level tab (Podcasts/Menu/Search) restores the home stack exactly as last
 *   left — including a note that was open there.
 * - Home while already on the inbox snaps the Today Hub to the current week (and scrolls to top) when
 *   it is on another week, otherwise does nothing. The inbox route makes that call since it owns the
 *   Today Hub state.
 */
internal sealed interface TabNavAction {
    /** Re-selecting the current non-home tab — do nothing. */
    data object NoOp : TabNavAction

    /**
     * Re-tapping Home while already on the inbox: the inbox route snaps the Today Hub to the current
     * week (and scrolls to top) if it is on another week, otherwise does nothing.
     */
    data object ReselectHome : TabNavAction

    /** Pop a note/editor (a drill-down from home) back to the inbox, keeping the Today Hub's week. */
    data object PopHome : TabNavAction

    /**
     * Navigate to a top-level tab, saving the outgoing stack and restoring the target's. Used for
     * sibling tabs and for Home from a sibling, so Home restores the home stack exactly as last left
     * (including a note that was open there).
     */
    data object NavigateTab : TabNavAction

    /** Plain push of a transient destination (e.g. create-inbox). */
    data object Push : TabNavAction
}

/**
 * Drill-downs reachable from home; Home from them pops back to the inbox.
 *
 * [AppRoute.CREATE_INBOX] is a transient push (the Add button) and must pop cleanly: if Home from it
 * went through the save/restore tab switch, its entry would be stashed in a saved back stack and then
 * leak back on top of the next tab. Treating it as an inbox child makes Home pop it instead.
 */
internal fun isInboxChildRoute(route: String?): Boolean =
    route == AppRoute.NOTE_PATTERN ||
        route == AppRoute.EDITOR_PATTERN ||
        route == AppRoute.CREATE_INBOX

/** Pure decision for what a shell tab tap should do. Side-effect free for unit testing. */
internal fun resolveTabNavigation(currentRoute: String?, targetRoute: String): TabNavAction =
    when (targetRoute) {
        AppRoute.INBOX -> when {
            currentRoute == AppRoute.INBOX -> TabNavAction.ReselectHome
            isInboxChildRoute(currentRoute) -> TabNavAction.PopHome
            else -> TabNavAction.NavigateTab
        }
        currentRoute -> TabNavAction.NoOp
        AppRoute.CREATE_INBOX -> TabNavAction.Push
        else -> TabNavAction.NavigateTab
    }

/**
 * Applies [resolveTabNavigation] to this controller. [onHomeReselected] is invoked when Home is
 * tapped while already on the inbox; the inbox route decides whether to snap the Today Hub to the
 * current week and scroll to top.
 */
internal fun NavHostController.navigateTab(
    currentRoute: String?,
    targetRoute: String,
    onHomeReselected: () -> Unit
) {
    when (resolveTabNavigation(currentRoute, targetRoute)) {
        TabNavAction.NoOp -> Unit
        TabNavAction.ReselectHome -> onHomeReselected()
        TabNavAction.PopHome -> popBackStack(AppRoute.INBOX, false)
        TabNavAction.NavigateTab -> navigate(targetRoute) {
            popUpTo(AppRoute.INBOX) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        TabNavAction.Push -> navigate(targetRoute) {
            launchSingleTop = true
        }
    }
}

/** Handles a tap on the shell sync indicator: sync inline when safe, otherwise open the Sync screen. */
internal fun onShellSyncClick(
    syncState: SyncUiState,
    appSyncViewModel: AppSyncViewModel,
    navController: NavHostController
) {
    when (syncState) {
        is SyncUiState.Ready -> when {
            !syncState.status.hasSyncWork -> Unit
            syncState.preflight.canSync -> appSyncViewModel.syncNow()
            else -> navController.openSyncScreen()
        }
        SyncUiState.Loading,
        is SyncUiState.Syncing,
        is SyncUiState.Success -> Unit
        is SyncUiState.Error -> navController.openSyncScreen()
    }
}

private fun NavHostController.openSyncScreen() {
    navigate(AppRoute.SYNC) { launchSingleTop = true }
}

/**
 * Registers a NavHost destination whose content paints on an opaque [Surface].
 *
 * Transitions are instant (no animation), but the NavHost still briefly composes the outgoing and
 * incoming destinations stacked during the swap. With transparent screens you see the previous
 * screen's content through the new one for a frame ("content over elkaar"). An opaque background
 * makes the incoming screen fully occlude the outgoing one, so every swap looks atomic. The colour
 * matches the root [Surface] in `AppRoot`, and the shell edge scrims still draw on top.
 */
internal fun NavGraphBuilder.opaqueComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route = route, arguments = arguments) { entry ->
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        content(entry)
    }
}
