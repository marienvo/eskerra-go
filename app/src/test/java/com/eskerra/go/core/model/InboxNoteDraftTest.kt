package com.eskerra.go.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxNoteDraftTest {

    @Test
    fun extractTitleLine_returnsFirstLineTrimmed() {
        assertEquals("My idea", InboxNoteDraft.extractTitleLine("  My idea  \nBody"))
    }

    @Test
    fun extractTitleLine_stripsExistingH1Prefix() {
        assertEquals("My idea", InboxNoteDraft.extractTitleLine("# My idea\nBody"))
    }

    @Test
    fun hasNonBlankTitle_falseWhenFirstLineBlank() {
        assertFalse(InboxNoteDraft.hasNonBlankTitle("\nBody"))
        assertFalse(InboxNoteDraft.hasNonBlankTitle("   "))
    }

    @Test
    fun hasNonBlankTitle_trueWhenFirstLineHasText() {
        assertTrue(InboxNoteDraft.hasNonBlankTitle("My idea"))
    }

    @Test
    fun toMarkdown_prefixesFirstLineWithH1() {
        assertEquals("# My idea\n\nBody line", InboxNoteDraft.toMarkdown("My idea\nBody line"))
    }

    @Test
    fun toMarkdown_keepsExistingH1Prefix() {
        assertEquals("# My idea\n\nBody", InboxNoteDraft.toMarkdown("# My idea\n\nBody"))
    }

    @Test
    fun toMarkdown_singleLineEndsWithSingleNewline() {
        assertEquals("# Title only\n", InboxNoteDraft.toMarkdown("Title only"))
    }

    @Test
    fun toFilenameStem_preservesSpacesAndCase() {
        assertEquals("Mijn idee", InboxNoteDraft.toFilenameStem("Mijn idee", nowEpochMillis = 1L))
    }

    @Test
    fun toFilenameStem_removesInvalidPathCharacters() {
        assertEquals(
            "Mynotebadname",
            InboxNoteDraft.toFilenameStem("My/note\\bad:name", nowEpochMillis = 1L)
        )
    }

    @Test
    fun toFilenameStem_fallsBackToTimestampWhenBlank() {
        assertEquals("note-999", InboxNoteDraft.toFilenameStem("   ", nowEpochMillis = 999))
    }
}
