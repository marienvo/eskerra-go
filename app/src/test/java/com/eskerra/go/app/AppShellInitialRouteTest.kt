package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellInitialRouteTest {

    @Test
    fun resumablePlayback_opensPodcasts() {
        assertEquals(
            AppRoute.PODCASTS,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.HOME,
                hasResumablePlayback = true
            )
        )
    }

    @Test
    fun lastPodcastMode_opensPodcastsWithoutResumablePlayback() {
        assertEquals(
            AppRoute.PODCASTS,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.PODCASTS,
                hasResumablePlayback = false
            )
        )
    }

    @Test
    fun defaultHome_opensInbox() {
        assertEquals(
            AppRoute.INBOX,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.HOME,
                hasResumablePlayback = false
            )
        )
    }

    @Test
    fun shellModeForRoute_mapsTopLevelRoutes() {
        assertEquals(AppShellMode.PODCASTS, shellModeForRoute(AppRoute.PODCASTS))
        assertEquals(AppShellMode.HOME, shellModeForRoute(AppRoute.INBOX))
        assertEquals(AppShellMode.HOME, shellModeForRoute(AppRoute.NOTE_PATTERN))
    }

    @Test
    fun shouldDismissSplashWithoutInbox_onlyForPodcastsRoute() {
        assertTrue(shouldDismissSplashWithoutInbox(AppRoute.PODCASTS))
        assertFalse(shouldDismissSplashWithoutInbox(AppRoute.INBOX))
    }
}
