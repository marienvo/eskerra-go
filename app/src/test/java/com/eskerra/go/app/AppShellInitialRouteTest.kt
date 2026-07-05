package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellInitialRouteTest {

    @Test
    fun resumablePlayback_opensPodcasts() {
        assertEquals(
            AppRoute.PODCASTS_GRAPH,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.HOME,
                hasResumablePlayback = true
            )
        )
    }

    @Test
    fun lastPodcastMode_opensPodcastsWithoutResumablePlayback() {
        assertEquals(
            AppRoute.PODCASTS_GRAPH,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.PODCASTS,
                hasResumablePlayback = false
            )
        )
    }

    @Test
    fun defaultHome_opensInbox() {
        assertEquals(
            AppRoute.HOME_GRAPH,
            resolveInitialShellRoute(
                preferredShellMode = AppShellMode.HOME,
                hasResumablePlayback = false
            )
        )
    }

    @Test
    fun shellModeForRouteHierarchy_mapsNestedGraphDestinations() {
        assertEquals(
            AppShellMode.PODCASTS,
            shellModeForRouteHierarchy(
                sequenceOf(AppRoute.PODCASTS, AppRoute.PODCASTS_GRAPH)
            )
        )
        assertEquals(
            AppShellMode.HOME,
            shellModeForRouteHierarchy(sequenceOf(AppRoute.INBOX, AppRoute.HOME_GRAPH))
        )
        assertEquals(AppShellMode.HOME, shellModeForRouteHierarchy(sequenceOf(AppRoute.HOME_GRAPH)))
    }

    @Test
    fun topLevelGraphRouteForHierarchy_mapsNestedGraphDestinations() {
        assertEquals(
            AppRoute.PODCASTS_GRAPH,
            topLevelGraphRouteForHierarchy(sequenceOf(AppRoute.PODCASTS, AppRoute.PODCASTS_GRAPH))
        )
        assertEquals(
            AppRoute.HOME_GRAPH,
            topLevelGraphRouteForHierarchy(sequenceOf(AppRoute.INBOX, AppRoute.HOME_GRAPH))
        )
    }

    @Test
    fun shouldDismissSplashWithoutInbox_onlyForPodcastsGraph() {
        assertTrue(shouldDismissSplashWithoutInbox(AppRoute.PODCASTS_GRAPH))
        assertFalse(shouldDismissSplashWithoutInbox(AppRoute.HOME_GRAPH))
    }

    @Test
    fun shouldShowNewNoteInput_onlyInHomeModeOnVaultReaderRoutes() {
        val noteRoute = AppRoute.note(NoteId("Inbox/A.md"))

        assertTrue(shouldShowNewNoteInput(AppRoute.INBOX, AppShellMode.HOME))
        assertTrue(shouldShowNewNoteInput(noteRoute, AppShellMode.HOME))

        assertFalse(shouldShowNewNoteInput(AppRoute.EDITOR_PATTERN, AppShellMode.HOME))
        assertTrue(shouldShowNewNoteInput(AppRoute.SEARCH, AppShellMode.HOME))
        assertTrue(shouldShowNewNoteInput(AppRoute.SEARCH_PATTERN, AppShellMode.HOME))
        assertTrue(shouldShowNewNoteInput(AppRoute.search("vault"), AppShellMode.HOME))
        assertFalse(shouldShowNewNoteInput(AppRoute.INBOX, AppShellMode.PODCASTS))
        assertFalse(shouldShowNewNoteInput(null, AppShellMode.HOME))
    }
}
