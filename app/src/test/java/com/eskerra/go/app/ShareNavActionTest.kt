package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareNavActionTest {

    @Test
    fun inboxKeepsTheUserWhereTheyAre() {
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(AppRoute.INBOX, AppRoute.HOME_GRAPH)
        )
    }

    @Test
    fun noteReaderKeepsTheUserWhereTheyAre() {
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(AppRoute.NOTE_PATTERN, AppRoute.HOME_GRAPH)
        )
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(
                AppRoute.note(com.eskerra.go.core.model.NoteId("Inbox/a.md")),
                AppRoute.HOME_GRAPH
            )
        )
    }

    @Test
    fun searchRoutesArePoppedSoTheShareCannotLandInTheQuery() {
        assertEquals(ShareNavAction.PopSearch, shareNavAction(AppRoute.SEARCH, AppRoute.HOME_GRAPH))
        assertEquals(
            ShareNavAction.PopSearch,
            shareNavAction(AppRoute.search("meeting"), AppRoute.HOME_GRAPH)
        )
    }

    @Test
    fun podcastModeSwitchesToHome() {
        assertEquals(
            ShareNavAction.SwitchToHome,
            shareNavAction(AppRoute.PODCASTS_GRAPH, AppRoute.PODCASTS_GRAPH)
        )
    }

    @Test
    fun unknownRouteSwitchesToHome() {
        assertEquals(ShareNavAction.SwitchToHome, shareNavAction(null, null))
        assertEquals(
            ShareNavAction.SwitchToHome,
            shareNavAction("some-other-route", AppRoute.HOME_GRAPH)
        )
    }
}
