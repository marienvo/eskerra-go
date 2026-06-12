package com.eskerra.go.core.inbox

import org.junit.Assert.assertEquals
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
    fun sanitizeFileName_fallsBackToTimestampWhenBlank() {
        assertEquals(
            "note-123",
            InboxMarkdownFileName.sanitizeFileName("   ", nowEpochMillis = 123)
        )
    }
}
