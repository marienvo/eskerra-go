package com.eskerra.go.core.inbox

import com.eskerra.go.core.model.InboxNoteDraft
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden vectors from `packages/eskerra-core/src/inboxComposeNote.test.ts`. */
class InboxComposeNoteTest {

    @Test
    fun parseComposeInput_titleOnly() {
        assertEquals(
            InboxNoteDraft.ParsedComposeInput(bodyAfterBlank = "", titleLine = "Meeting notes"),
            InboxNoteDraft.parseComposeInput("Meeting notes")
        )
    }

    @Test
    fun parseComposeInput_titleAndBody() {
        assertEquals(
            InboxNoteDraft.ParsedComposeInput(
                bodyAfterBlank = "Line 2\nLine 3",
                titleLine = "Meeting notes"
            ),
            InboxNoteDraft.parseComposeInput("Meeting notes\n\nLine 2\nLine 3")
        )
    }

    @Test
    fun toMarkdown_titleOnly() {
        assertEquals("# Meeting notes\n", InboxNoteDraft.toMarkdown("Meeting notes"))
    }

    @Test
    fun toMarkdown_titleAndBody() {
        assertEquals(
            "# Meeting notes\n\nLine 2\nLine 3",
            InboxNoteDraft.toMarkdown("Meeting notes\n\nLine 2\nLine 3")
        )
    }

    @Test
    fun toMarkdown_keepsSpecialCharactersInTitle() {
        assertEquals(
            "# Sprint #12: done?!\n\nBody",
            InboxNoteDraft.toMarkdown("Sprint #12: done?!\n\nBody")
        )
    }

    @Test
    fun fromMarkdownToComposeInput_invertsToMarkdown() {
        val compose = "Meeting notes\n\nLine 2\nLine 3"
        val file = InboxNoteDraft.toMarkdown(compose)
        assertEquals(compose, InboxNoteDraft.fromMarkdownToComposeInput(file))
    }

    @Test
    fun fromMarkdownToComposeInput_titleOnlyH1File() {
        assertEquals(
            "Meeting notes",
            InboxNoteDraft.fromMarkdownToComposeInput("# Meeting notes\n")
        )
    }

    @Test
    fun fromMarkdownToComposeInput_stripsBlankLinesAfterH1() {
        assertEquals(
            "Title\n\nBody",
            InboxNoteDraft.fromMarkdownToComposeInput("# Title\n\n\nBody")
        )
    }

    @Test
    fun fromMarkdownToComposeInput_fallsBackWhenNoH1() {
        assertEquals(
            "Plain first\n\nSecond",
            InboxNoteDraft.fromMarkdownToComposeInput("Plain first\nSecond")
        )
    }
}
