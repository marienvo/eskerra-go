package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {

    // --- Home (inbox) re-selection -------------------------------------------------------------

    @Test
    fun home_whileOnInbox_reselectsHome() {
        assertEquals(
            TopLevelNavAction.ReselectHome,
            resolveTopLevelNavigation(currentRoute = AppRoute.INBOX, targetRoute = AppRoute.INBOX)
        )
        assertEquals(
            TopLevelNavAction.ReselectHome,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.INBOX,
                targetRoute = AppRoute.HOME_GRAPH
            )
        )
    }

    @Test
    fun home_fromNote_popsToInbox() {
        assertEquals(
            TopLevelNavAction.PopHome,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.INBOX
            )
        )
        assertEquals(
            TopLevelNavAction.PopHome,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.HOME_GRAPH
            )
        )
    }

    @Test
    fun home_fromPodcastStackNote_switchesToHomeGraph() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.HOME_GRAPH,
                currentTopLevelRoute = AppRoute.PODCASTS_GRAPH
            )
        )
    }

    @Test
    fun home_fromEditor_popsToInbox() {
        assertEquals(
            TopLevelNavAction.PopHome,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.EDITOR_PATTERN,
                targetRoute = AppRoute.INBOX
            )
        )
    }

    @Test
    fun home_fromPodcasts_restoresHomeStack() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.PODCASTS,
                targetRoute = AppRoute.INBOX
            )
        )
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.PODCASTS,
                targetRoute = AppRoute.HOME_GRAPH
            )
        )
    }

    @Test
    fun home_fromSearch_restoresHomeStack() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(currentRoute = AppRoute.SEARCH, targetRoute = AppRoute.INBOX)
        )
    }

    // --- Sibling tabs --------------------------------------------------------------------------

    @Test
    fun podcasts_fromInbox_navigatesTab() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.INBOX,
                targetRoute = AppRoute.PODCASTS
            )
        )
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.INBOX,
                targetRoute = AppRoute.PODCASTS_GRAPH
            )
        )
    }

    @Test
    fun podcasts_whileOnPodcasts_isNoOp() {
        assertEquals(
            TopLevelNavAction.NoOp,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.PODCASTS,
                targetRoute = AppRoute.PODCASTS
            )
        )
        assertEquals(
            TopLevelNavAction.NoOp,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.PODCASTS,
                targetRoute = AppRoute.PODCASTS_GRAPH
            )
        )
    }

    @Test
    fun podcasts_fromNote_navigatesTab() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.PODCASTS
            )
        )
    }

    @Test
    fun note_savedInHomeStack_survivesTabRoundTrip() {
        // A note is a drill-down, not a transient: switching to Podcasts saves the home stack (note
        // included) rather than popping it, and Home later restores it.
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.PODCASTS_GRAPH
            )
        )
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(
                currentRoute = AppRoute.PODCASTS,
                targetRoute = AppRoute.HOME_GRAPH
            )
        )
    }

    // --- Edge: unknown current route -----------------------------------------------------------

    @Test
    fun home_fromNullRoute_navigatesToInbox() {
        assertEquals(
            TopLevelNavAction.NavigateTab,
            resolveTopLevelNavigation(currentRoute = null, targetRoute = AppRoute.INBOX)
        )
    }

    // --- isInboxChildRoute ---------------------------------------------------------------------

    @Test
    fun inboxChildRoutes_recognizeNoteAndEditor() {
        assertTrue(isInboxChildRoute(AppRoute.NOTE_PATTERN))
        assertTrue(isInboxChildRoute(AppRoute.EDITOR_PATTERN))
    }

    @Test
    fun inboxChildRoutes_rejectTopLevelAndNull() {
        assertFalse(isInboxChildRoute(AppRoute.INBOX))
        assertFalse(isInboxChildRoute(AppRoute.PODCASTS))
        assertFalse(isInboxChildRoute(null))
    }

    // --- isSearchRoute ---------------------------------------------------------------------------

    @Test
    fun isSearchRoute_recognizesGraphPatternAndConcreteQueryRoutes() {
        assertTrue(AppRoute.isSearchRoute(AppRoute.SEARCH_PATTERN))
        assertTrue(AppRoute.isSearchRoute(AppRoute.SEARCH))
        assertTrue(AppRoute.isSearchRoute(AppRoute.search("meeting notes")))
        assertFalse(AppRoute.isSearchRoute(AppRoute.INBOX))
        assertFalse(AppRoute.isSearchRoute(null))
    }
}
