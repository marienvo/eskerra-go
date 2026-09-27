package com.eskerra.go.app

internal fun shouldShowNewNoteInput(currentRoute: String?): Boolean {
    // The search route also shows the pill: in search mode it is the live search input for the
    // results.
    return currentRoute == AppRoute.INBOX ||
        AppRoute.isSearchRoute(currentRoute) ||
        AppRoute.isNoteReaderRoute(currentRoute)
}
