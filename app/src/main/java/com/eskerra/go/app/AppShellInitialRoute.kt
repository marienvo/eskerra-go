package com.eskerra.go.app

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.eskerra.go.core.model.AppShellMode

internal fun resolveInitialShellRoute(
    preferredShellMode: AppShellMode,
    hasResumablePlayback: Boolean
): String = when {
    hasResumablePlayback -> AppRoute.PODCASTS_GRAPH
    preferredShellMode == AppShellMode.PODCASTS -> AppRoute.PODCASTS_GRAPH
    else -> AppRoute.HOME_GRAPH
}

internal fun shellModeForDestination(destination: NavDestination?): AppShellMode? =
    shellModeForRouteHierarchy(destination?.hierarchy?.map { it.route }.orEmpty())

internal fun shellModeForRouteHierarchy(routeHierarchy: Sequence<String?>): AppShellMode? {
    val routes = routeHierarchy.toSet()
    return when {
        AppRoute.PODCASTS_GRAPH in routes -> AppShellMode.PODCASTS
        AppRoute.HOME_GRAPH in routes -> AppShellMode.HOME
        else -> null
    }
}

internal fun shouldDismissSplashWithoutInbox(initialRoute: String): Boolean =
    initialRoute == AppRoute.PODCASTS_GRAPH
