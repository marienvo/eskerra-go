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
 * reset/restore edge cases — can be unit-tested without a live NavController.
 *
 * The home (inbox) destination is the NavHost start destination, so it is always at the root of the
 * back stack. Sibling top-level tabs (podcasts, menu) use the multiple-back-stack save/restore
 * pattern so their state survives a round trip. Re-tapping a tab does the least surprising thing:
 *
 * - Re-tapping the current tab is a no-op.
 * - Home while on a note/editor (a drill-down from home) pops to home **and resets** it (Today Hub
 *   back to the current week, list scrolled to top).
 * - Home while on another top-level tab pops to home **and restores** it as last seen.
 */
internal sealed interface TabNavAction {
    /** Already here — do nothing. */
    data object NoOp : TabNavAction

    /** Pop back to the home start destination; [reset] requests a fresh home view. */
    data class PopHome(val reset: Boolean) : TabNavAction

    /** Switch to a sibling top-level tab, saving the outgoing stack and restoring the target's. */
    data object NavigateTab : TabNavAction

    /** Plain push of a transient destination (e.g. create-inbox). */
    data object Push : TabNavAction
}

/** Note reader / editor are drill-downs from home; re-tapping Home from them resets home. */
internal fun isInboxChildRoute(route: String?): Boolean =
    route == AppRoute.NOTE_PATTERN || route == AppRoute.EDITOR_PATTERN

/** Pure decision for what a shell tab tap should do. Side-effect free for unit testing. */
internal fun resolveTabNavigation(currentRoute: String?, targetRoute: String): TabNavAction =
    when (targetRoute) {
        AppRoute.INBOX -> when {
            currentRoute == AppRoute.INBOX -> TabNavAction.NoOp
            isInboxChildRoute(currentRoute) -> TabNavAction.PopHome(reset = true)
            else -> TabNavAction.PopHome(reset = false)
        }
        currentRoute -> TabNavAction.NoOp
        AppRoute.CREATE_INBOX -> TabNavAction.Push
        else -> TabNavAction.NavigateTab
    }

/**
 * Applies [resolveTabNavigation] to this controller. [onHomeReset] is invoked (before popping) when
 * home should reset to a fresh view; the inbox route consumes it to reset Today Hub and scroll
 * position.
 */
internal fun NavHostController.navigateTab(
    currentRoute: String?,
    targetRoute: String,
    onHomeReset: () -> Unit
) {
    when (val action = resolveTabNavigation(currentRoute, targetRoute)) {
        TabNavAction.NoOp -> Unit
        is TabNavAction.PopHome -> {
            if (action.reset) onHomeReset()
            popBackStack(AppRoute.INBOX, false)
        }
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
