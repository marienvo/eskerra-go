package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode

internal fun shouldShowNewNoteInput(currentRoute: String?, shellMode: AppShellMode?): Boolean {
    if (shellMode != AppShellMode.HOME) {
        return false
    }
    // The note reader's back-stack route is the pattern `note/{noteId}` (arguments are separate), so
    // match it explicitly alongside concrete note routes to keep the pill visible while reading. The
    // search route also shows the pill: in search mode it is the live search input for the results.
    return currentRoute == AppRoute.INBOX ||
        AppRoute.isSearchRoute(currentRoute) ||
        currentRoute == AppRoute.NOTE_PATTERN ||
        AppRoute.isConcreteNoteRoute(currentRoute)
}
