package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareNavActionTest {

    @Test
    fun inboxKeepsTheUserWhereTheyAre() {
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(AppRoute.INBOX)
        )
    }

    @Test
    fun noteReaderKeepsTheUserWhereTheyAre() {
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(AppRoute.NOTE_PATTERN)
        )
        assertEquals(
            ShareNavAction.NoOp,
            shareNavAction(
                AppRoute.note(com.eskerra.go.core.model.NoteId("Inbox/a.md"))
            )
        )
    }

    @Test
    fun searchRoutesArePoppedSoTheShareCannotLandInTheQuery() {
        assertEquals(ShareNavAction.PopSearch, shareNavAction(AppRoute.SEARCH))
        assertEquals(
            ShareNavAction.PopSearch,
            shareNavAction(AppRoute.search("meeting"))
        )
    }

    @Test
    fun unknownRouteKeepsTheUserWhereTheyAre() {
        assertEquals(ShareNavAction.NoOp, shareNavAction(null))
        assertEquals(ShareNavAction.NoOp, shareNavAction("some-other-route"))
    }
}
