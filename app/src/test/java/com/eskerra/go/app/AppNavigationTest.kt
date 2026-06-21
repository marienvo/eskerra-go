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
            TabNavAction.ReselectHome,
            resolveTabNavigation(currentRoute = AppRoute.INBOX, targetRoute = AppRoute.INBOX)
        )
    }

    @Test
    fun home_fromNote_popsToInbox() {
        assertEquals(
            TabNavAction.PopHome,
            resolveTabNavigation(currentRoute = AppRoute.NOTE_PATTERN, targetRoute = AppRoute.INBOX)
        )
    }

    @Test
    fun home_fromEditor_popsToInbox() {
        assertEquals(
            TabNavAction.PopHome,
            resolveTabNavigation(
                currentRoute = AppRoute.EDITOR_PATTERN,
                targetRoute = AppRoute.INBOX
            )
        )
    }

    @Test
    fun home_fromCreateInbox_popsToInbox() {
        // CREATE_INBOX is a transient push; Home must pop it cleanly rather than stashing it in a
        // saved back stack (which would leak "New note" back on top of the next tab switch).
        assertEquals(
            TabNavAction.PopHome,
            resolveTabNavigation(
                currentRoute = AppRoute.CREATE_INBOX,
                targetRoute = AppRoute.INBOX
            )
        )
    }

    @Test
    fun home_fromPodcasts_restoresHomeStack() {
        assertEquals(
            TabNavAction.NavigateTab,
            resolveTabNavigation(currentRoute = AppRoute.PODCASTS, targetRoute = AppRoute.INBOX)
        )
    }

    @Test
    fun home_fromSearch_restoresHomeStack() {
        assertEquals(
            TabNavAction.NavigateTab,
            resolveTabNavigation(currentRoute = AppRoute.SEARCH, targetRoute = AppRoute.INBOX)
        )
    }

    // --- Sibling tabs --------------------------------------------------------------------------

    @Test
    fun podcasts_fromInbox_navigatesTab() {
        assertEquals(
            TabNavAction.NavigateTab,
            resolveTabNavigation(currentRoute = AppRoute.INBOX, targetRoute = AppRoute.PODCASTS)
        )
    }

    @Test
    fun podcasts_whileOnPodcasts_isNoOp() {
        assertEquals(
            TabNavAction.NoOp,
            resolveTabNavigation(currentRoute = AppRoute.PODCASTS, targetRoute = AppRoute.PODCASTS)
        )
    }

    @Test
    fun podcasts_fromNote_navigatesTab() {
        assertEquals(
            TabNavAction.NavigateTab,
            resolveTabNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.PODCASTS
            )
        )
    }

    // --- Transient push ------------------------------------------------------------------------

    @Test
    fun createInbox_fromInbox_isPush() {
        assertEquals(
            TabNavAction.Push,
            resolveTabNavigation(currentRoute = AppRoute.INBOX, targetRoute = AppRoute.CREATE_INBOX)
        )
    }

    @Test
    fun createInbox_fromNote_isPush() {
        assertEquals(
            TabNavAction.Push,
            resolveTabNavigation(
                currentRoute = AppRoute.NOTE_PATTERN,
                targetRoute = AppRoute.CREATE_INBOX
            )
        )
    }

    // --- Edge: unknown current route -----------------------------------------------------------

    @Test
    fun home_fromNullRoute_navigatesToInbox() {
        assertEquals(
            TabNavAction.NavigateTab,
            resolveTabNavigation(currentRoute = null, targetRoute = AppRoute.INBOX)
        )
    }

    // --- isInboxChildRoute ---------------------------------------------------------------------

    @Test
    fun inboxChildRoutes_recognizeNoteEditorAndCreateInbox() {
        assertTrue(isInboxChildRoute(AppRoute.NOTE_PATTERN))
        assertTrue(isInboxChildRoute(AppRoute.EDITOR_PATTERN))
        assertTrue(isInboxChildRoute(AppRoute.CREATE_INBOX))
    }

    @Test
    fun inboxChildRoutes_rejectTopLevelAndNull() {
        assertFalse(isInboxChildRoute(AppRoute.INBOX))
        assertFalse(isInboxChildRoute(AppRoute.PODCASTS))
        assertFalse(isInboxChildRoute(null))
    }
}
