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
    fun editorRouteDelegatesToNoteRouteCodec() {
        val noteId = NoteId("Inbox/First.md")
        val expected = "editor/${NoteRouteCodec.encode(noteId.value)}"
        assertEquals(expected, AppRoute.editor(noteId))
    }

    @Test
    fun decodeNoteIdDelegatesToNoteRouteCodec() {
        val raw = NoteRouteCodec.encode("Inbox/First.md")
        assertEquals(NoteId("Inbox/First.md"), AppRoute.decodeNoteId(raw))
    }

    @Test
    fun decodeEditorNoteIdDelegatesToNoteRouteCodec() {
        val raw = NoteRouteCodec.encode("Inbox/First.md")
        assertEquals(NoteId("Inbox/First.md"), AppRoute.decodeEditorNoteId(raw))
    }

    @Test
    fun routeEncodingHandlesNestedPathsAndSpaces() {
        val noteId = NoteId("Projects/My App/Meeting Notes.md")
        val encoded = NoteRouteCodec.encode(noteId.value)
        val noteRoute = AppRoute.note(noteId)
        val editorRoute = AppRoute.editor(noteId)

        assertEquals(
            NoteId("Projects/My App/Meeting Notes.md"),
            AppRoute.decodeNoteId(encoded)
        )
        assertEquals(
            NoteId("Projects/My App/Meeting Notes.md"),
            AppRoute.decodeEditorNoteId(encoded)
        )
        assertEquals("note/$encoded", noteRoute)
        assertEquals("editor/$encoded", editorRoute)
    }

    @Test
    fun invalidRouteIdsDoNotCrash() {
        val decoded = AppRoute.decodeNoteId("")
        assertEquals(NoteId(""), decoded)
    }
}
