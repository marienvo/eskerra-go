package com.eskerra.go.app

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.eskerra.go.core.model.AppShellMode

/**
 * Home is the default landing tab. Only audio that is genuinely playing at launch (continued in
 * the background, or opened from the notification) may start the app on Episodes; a merely
 * resumable episode stays in the mini player and does not move the user off Home.
 */
internal fun resolveInitialShellRoute(
    isActivelyPlaying: Boolean,
    hasPendingShare: Boolean = false
): String = when {
    // A share was the reason the app opened: it wins over active playback, because the
    // compose pill only exists in the Home graph.
    hasPendingShare -> AppRoute.HOME_GRAPH
    isActivelyPlaying -> AppRoute.PODCASTS_GRAPH
    else -> AppRoute.HOME_GRAPH
}

internal fun topLevelGraphRouteForDestination(destination: NavDestination?): String? =
    topLevelGraphRouteForHierarchy(destination?.hierarchy?.map { it.route }.orEmpty())

internal fun shellModeForRouteHierarchy(routeHierarchy: Sequence<String?>): AppShellMode? =
    when (topLevelGraphRouteForHierarchy(routeHierarchy)) {
        AppRoute.PODCASTS_GRAPH -> AppShellMode.PODCASTS
        AppRoute.HOME_GRAPH -> AppShellMode.HOME
        else -> null
    }

internal fun topLevelGraphRouteForHierarchy(routeHierarchy: Sequence<String?>): String? {
    val routes = routeHierarchy.toSet()
    return when {
        AppRoute.PODCASTS_GRAPH in routes -> AppRoute.PODCASTS_GRAPH
        AppRoute.HOME_GRAPH in routes -> AppRoute.HOME_GRAPH
        else -> null
    }
}

internal fun shouldDismissSplashWithoutInbox(initialRoute: String): Boolean =
    initialRoute == AppRoute.PODCASTS_GRAPH
