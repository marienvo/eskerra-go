package com.eskerra.go.core.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxMarkdownFileNameTest {

    @Test
    fun pickNextInboxMarkdownFileName_picksBaseThenIncrements() {
        assertEquals(
            "team-ideas.md",
            InboxMarkdownFileName.pickNextInboxMarkdownFileName("team-ideas", emptySet())
        )
        assertEquals(
            "team-ideas-3.md",
            InboxMarkdownFileName.pickNextInboxMarkdownFileName(
                "team-ideas",
                setOf("team-ideas.md", "team-ideas-2.md")
            )
        )
    }

    @Test
    fun sanitizeFileName_stripsIllegalCharactersAndCollapsesWhitespace() {
        assertEquals(
            "Mynotebadname",
            InboxMarkdownFileName.sanitizeFileName("My/note\\bad:name", nowEpochMillis = 1L)
        )
    }

    @Test
    fun sanitizeFileName_fallsBackToPortableNameWhenBlank() {
        assertEquals(
            "untitled",
            InboxMarkdownFileName.sanitizeFileName("   ", nowEpochMillis = 123)
        )
    }

    @Test
    fun sanitizeFileName_isPortableAcrossWindowsAndMacos() {
        assertEquals("_CON", InboxMarkdownFileName.sanitizeFileName(" CON. "))
        assertEquals("RoadMap", InboxMarkdownFileName.sanitizeFileName("Road/Map"))
        assertEquals("caf\u00e9", InboxMarkdownFileName.sanitizeFileName("cafe\u0301"))
        assertEquals("quotes", InboxMarkdownFileName.sanitizeFileName("'\u201Cquotes\u201D`"))
    }

    @Test
    fun pickNextInboxMarkdownFileName_usesPortableCollisionKeyAndByteBudget() {
        assertEquals(
            "Cafe-2.md",
            InboxMarkdownFileName.pickNextInboxMarkdownFileName("Cafe", setOf("cafe.md"))
        )
        val fileName = InboxMarkdownFileName.pickNextInboxMarkdownFileName(
            "\uD83D\uDE00".repeat(100),
            emptySet()
        )
        assertTrue(fileName.toByteArray().size <= 255)
    }
}
