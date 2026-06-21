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
 * Each tab is a nested graph; `homeGraph` (start = inbox) is the NavHost start destination, so the
 * inbox is always at the root of the back stack. Switching tabs uses the multiple-back-stack
 * save/restore pattern (popUpTo the graph start with `saveState`, `restoreState` on the way in) so a
 * tab's stack — including a note opened there — survives a round trip. Drill-downs (note/editor) and
 * the create-inbox screen are shared top-level destinations that push onto whichever tab stack is
 * active. The behaviour:
 *
 * - Re-tapping a non-home tab that is already current is a no-op.
 * - Home from a note/editor (a drill-down from home) pops back to the inbox; the Today Hub keeps the
 *   week it was on (no reset).
 * - Home from another top-level tab (Podcasts/Menu/Search) restores the home stack exactly as last
 *   left — including a note that was open there.
 * - Home while already on the inbox snaps the Today Hub to the current week (and scrolls to top) when
 *   it is on another week, otherwise does nothing. The inbox route makes that call since it owns the
 *   Today Hub state.
 * - Switching tabs while a transient destination (create-inbox) is on top first pops the transient
 *   without saving it, then does the save/restore switch — so the transient can never be stashed in a
 *   saved tab stack and leak back on top of the next tab (the "New note on Home" bug).
 */
internal sealed interface TopLevelNavAction {
    /** Re-selecting the current non-home tab — do nothing. */
    data object NoOp : TopLevelNavAction

    /**
     * Re-tapping Home while already on the inbox: the inbox route snaps the Today Hub to the current
     * week (and scrolls to top) if it is on another week, otherwise does nothing.
     */
    data object ReselectHome : TopLevelNavAction

    /** Pop a note/editor (a drill-down from home) back to the inbox, keeping the Today Hub's week. */
    data object PopHome : TopLevelNavAction

    /**
     * Navigate to a top-level tab, saving the outgoing stack and restoring the target's. Used for
     * sibling tabs and for Home from a sibling, so Home restores the home stack exactly as last left
     * (including a note that was open there).
     */
    data object NavigateTab : TopLevelNavAction

    /**
     * Switching tabs while a transient destination (create-inbox) is on top: pop the transient first
     * (no save), then do the [NavigateTab] save/restore switch from the route underneath. Keeps the
     * transient out of any saved tab stack.
     */
    data object PopTransientThenNavigateTab : TopLevelNavAction

    /** Plain push of a transient destination (e.g. create-inbox). */
    data object Push : TopLevelNavAction
}

/**
 * Transient destinations: pushed contextually (the Add button) and never meant to survive a tab
 * switch. They must be popped before any save/restore switch so they cannot be stashed in a saved
 * back stack and leak back on top of the next tab.
 */
internal val TRANSIENT_ROUTES: Set<String> = setOf(AppRoute.CREATE_INBOX)

internal fun isTransientRoute(route: String?): Boolean = route in TRANSIENT_ROUTES

/**
 * Drill-downs reachable from home; Home from them pops back to the inbox.
 *
 * [AppRoute.CREATE_INBOX] is a transient push (the Add button) and must pop cleanly: if Home from it
 * went through the save/restore tab switch, its entry would be stashed in a saved back stack and then
 * leak back on top of the next tab. Treating it as an inbox child makes Home pop it instead.
 */
internal fun isInboxChildRoute(route: String?): Boolean = route == AppRoute.NOTE_PATTERN ||
    route == AppRoute.EDITOR_PATTERN ||
    route == AppRoute.CREATE_INBOX

private fun isHomeRoute(route: String?): Boolean =
    route == AppRoute.INBOX || route == AppRoute.HOME_GRAPH

private fun isPodcastsRoute(route: String?): Boolean =
    route == AppRoute.PODCASTS || route == AppRoute.PODCASTS_GRAPH

private fun inferredTopLevelRoute(route: String?): String? = when {
    isHomeRoute(route) || isInboxChildRoute(route) -> AppRoute.HOME_GRAPH
    isPodcastsRoute(route) -> AppRoute.PODCASTS_GRAPH
    else -> null
}

/** Pure decision for what a shell tab tap should do. Side-effect free for unit testing. */
internal fun resolveTopLevelNavigation(
    currentRoute: String?,
    targetRoute: String,
    currentTopLevelRoute: String? = inferredTopLevelRoute(currentRoute)
): TopLevelNavAction = when {
    isHomeRoute(targetRoute) -> when {
        currentRoute == AppRoute.INBOX -> TopLevelNavAction.ReselectHome
        isInboxChildRoute(currentRoute) && currentTopLevelRoute == AppRoute.HOME_GRAPH ->
            TopLevelNavAction.PopHome
        isTransientRoute(currentRoute) -> TopLevelNavAction.PopTransientThenNavigateTab
        else -> TopLevelNavAction.NavigateTab
    }
    isPodcastsRoute(targetRoute) && currentTopLevelRoute == AppRoute.PODCASTS_GRAPH ->
        TopLevelNavAction.NoOp
    currentRoute == targetRoute -> TopLevelNavAction.NoOp
    targetRoute == AppRoute.CREATE_INBOX -> TopLevelNavAction.Push
    isTransientRoute(currentRoute) -> TopLevelNavAction.PopTransientThenNavigateTab
    else -> TopLevelNavAction.NavigateTab
}

/**
 * Applies [resolveTopLevelNavigation] to this controller. [onHomeReselected] is invoked when Home is
 * tapped while already on the inbox; the inbox route decides whether to snap the Today Hub to the
 * current week and scroll to top.
 */
internal fun NavHostController.navigateTab(
    currentRoute: String?,
    targetRoute: String,
    currentTopLevelRoute: String? = inferredTopLevelRoute(currentRoute),
    onHomeReselected: () -> Unit
) {
    when (resolveTopLevelNavigation(currentRoute, targetRoute, currentTopLevelRoute)) {
        TopLevelNavAction.NoOp -> Unit
        TopLevelNavAction.ReselectHome -> onHomeReselected()
        TopLevelNavAction.PopHome -> popBackStack(AppRoute.INBOX, false)
        TopLevelNavAction.NavigateTab -> navigateTopLevel(targetRoute)
        TopLevelNavAction.PopTransientThenNavigateTab -> {
            // Drop the transient (create-inbox) without saving it, then run the normal save/restore
            // switch from the route underneath, so the transient never lands in a saved tab stack.
            popBackStack()
            navigateTopLevel(targetRoute)
        }
        TopLevelNavAction.Push -> navigate(targetRoute) {
            launchSingleTop = true
        }
    }
}

/** Multiple-back-stack switch: save the outgoing tab's stack and restore the target's. */
private fun NavHostController.navigateTopLevel(targetRoute: String) = navigate(targetRoute) {
    popUpTo(AppRoute.INBOX) { saveState = true }
    launchSingleTop = true
    restoreState = true
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
