package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode

internal fun resolveInitialShellRoute(
    preferredShellMode: AppShellMode,
    hasResumablePlayback: Boolean
): String = when {
    hasResumablePlayback -> AppRoute.PODCASTS
    preferredShellMode == AppShellMode.PODCASTS -> AppRoute.PODCASTS
    else -> AppRoute.INBOX
}

internal fun shellModeForRoute(route: String?): AppShellMode? = when (route) {
    AppRoute.PODCASTS -> AppShellMode.PODCASTS
    AppRoute.INBOX,
    AppRoute.NOTE_PATTERN,
    AppRoute.EDITOR_PATTERN -> AppShellMode.HOME
    else -> null
}

internal fun shouldDismissSplashWithoutInbox(initialRoute: String): Boolean =
    initialRoute == AppRoute.PODCASTS
