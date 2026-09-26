package com.eskerra.go.app

/**
 * Where a share must send the app so the compose pill is on screen. The pill shows on the inbox,
 * a search route, or a note reader — and search is explicitly not a target,
 * because there the pill drives the query rather than the draft.
 */
internal enum class ShareNavAction {
    /** The pill is already visible and pointed at the draft; keep the user where they are. */
    NoOp,

    /** In search: leave the search route so the pill returns to note mode. */
    PopSearch,

}

internal fun shareNavAction(currentRoute: String?): ShareNavAction =
    when {
        AppRoute.isSearchRoute(currentRoute) -> ShareNavAction.PopSearch
        else -> ShareNavAction.NoOp
    }
