package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode

/**
 * Where a share must send the app so the compose pill is on screen. The pill only shows in Home
 * mode, on the inbox, a search route, or a note reader — and search is explicitly not a target,
 * because there the pill drives the query rather than the draft.
 */
internal enum class ShareNavAction {
    /** The pill is already visible and pointed at the draft; keep the user where they are. */
    NoOp,

    /** In search: leave the search route so the pill returns to note mode. */
    PopSearch,

    /** In podcasts (or anywhere without the pill): switch to the Home graph. */
    SwitchToHome
}

internal fun shareNavAction(currentRoute: String?, currentTopLevelRoute: String?): ShareNavAction =
    when {
        AppRoute.isSearchRoute(currentRoute) -> ShareNavAction.PopSearch
        currentTopLevelRoute != AppRoute.HOME_GRAPH -> ShareNavAction.SwitchToHome
        shouldShowNewNoteInput(currentRoute, AppShellMode.HOME) -> ShareNavAction.NoOp
        else -> ShareNavAction.SwitchToHome
    }
