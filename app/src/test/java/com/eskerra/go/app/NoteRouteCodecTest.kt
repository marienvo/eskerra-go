package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRouteCodecTest {

    @Test
    fun noteRouteRoundTripsNestedNotePath() {
        val noteId = NoteId("Inbox/First.md")
        val encoded = NoteRouteCodec.encode(noteId.value)
        val decoded = NoteRouteCodec.decode(encoded)
        assertEquals(noteId.value, decoded)
    }

    @Test
    fun noteRouteRoundTripsSpacesAndSymbols() {
        val values = listOf(
            "Inbox/My Note.md",
            "Notes/v1.0 (draft).md",
            "Projects/foo+bar.md"
        )
        values.forEach { value ->
            val encoded = NoteRouteCodec.encode(value)
            val decoded = NoteRouteCodec.decode(encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun blankValuesRoundTripForReaderValidation() {
        val encoded = NoteRouteCodec.encode("")
        val decoded = NoteRouteCodec.decode(encoded)
        assertEquals("", decoded)
    }

    @Test
    fun traversalLikeDecodedValuesRoundTripForReaderValidation() {
        val values = listOf(
            "../secret.md",
            "Inbox/../secret.md"
        )
        values.forEach { value ->
            val encoded = NoteRouteCodec.encode(value)
            val decoded = NoteRouteCodec.decode(encoded)
            assertEquals(value, decoded)
        }
    }
}
