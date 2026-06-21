package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode

internal fun shouldShowNewNoteInput(currentRoute: String?, shellMode: AppShellMode?): Boolean {
    if (shellMode != AppShellMode.HOME) {
        return false
    }
    return currentRoute == AppRoute.INBOX || AppRoute.isConcreteNoteRoute(currentRoute)
}
