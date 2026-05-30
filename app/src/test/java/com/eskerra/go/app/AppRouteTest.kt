package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {

    @Test
    fun noteRouteDelegatesToNoteRouteCodec() {
        val noteId = NoteId("Inbox/First.md")
        val expected = "note/${NoteRouteCodec.encode(noteId.value)}"
        assertEquals(expected, AppRoute.note(noteId))
    }

    @Test
    fun decodeNoteIdDelegatesToNoteRouteCodec() {
        val raw = NoteRouteCodec.encode("Inbox/First.md")
        assertEquals(NoteId("Inbox/First.md"), AppRoute.decodeNoteId(raw))
    }

    @Test
    fun invalidRouteIdsDoNotCrash() {
        val decoded = AppRoute.decodeNoteId("")
        assertEquals(NoteId(""), decoded)
    }
}
