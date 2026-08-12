package com.eskerra.go.app

import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellInitialRouteTest {

    @Test
    fun activePlayback_opensPodcasts() {
        assertEquals(
            AppRoute.PODCASTS_GRAPH,
            resolveInitialShellRoute(isActivelyPlaying = true)
        )
    }

    @Test
    fun pendingShare_beatsActivePlayback() {
        assertEquals(
            AppRoute.HOME_GRAPH,
            resolveInitialShellRoute(
                isActivelyPlaying = true,
                hasPendingShare = true
            )
        )
    }

    /** A paused-but-resumable episode must not steal the launch away from Notes. */
    @Test
    fun resumableButNotPlaying_opensHome() {
        assertEquals(
            AppRoute.HOME_GRAPH,
            resolveInitialShellRoute(isActivelyPlaying = false)
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
