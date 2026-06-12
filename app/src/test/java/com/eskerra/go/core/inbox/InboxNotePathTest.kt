package com.eskerra.go.core.inbox

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxNotePathTest {

    @Test
    fun isInboxRelativePath_acceptsDirectInboxChildrenOnly() {
        assertTrue(InboxNotePath.isInboxRelativePath("Inbox/foo.md"))
        assertFalse(InboxNotePath.isInboxRelativePath("General/foo.md"))
        assertFalse(InboxNotePath.isInboxRelativePath("Inbox/nested/foo.md"))
    }

    @Test
    fun resolveCanonicalDeleteNote_matchesExactId() {
        val note = summary("Inbox/foo.md")
        assertEquals(
            note,
            InboxNotePath.resolveCanonicalDeleteNote(NoteId("Inbox/foo.md"), listOf(note))
        )
    }

    @Test
    fun resolveCanonicalDeleteNote_matchesUniqueFilename() {
        val note = summary("Inbox/foo.md")
        assertEquals(
            note,
            InboxNotePath.resolveCanonicalDeleteNote(NoteId("stale-uri/Inbox/foo.md"), listOf(note))
        )
    }

    @Test
    fun resolveCanonicalDeleteNote_returnsNullForAmbiguousFilename() {
        val ambiguous = listOf(
            summary("Inbox/foo.md", title = "First"),
            summary("Inbox/nested/foo.md", title = "Second")
        )
        assertNull(
            InboxNotePath.resolveCanonicalDeleteNote(NoteId("stale/Inbox/foo.md"), ambiguous)
        )
    }

    private fun summary(path: String, title: String = "Title") = NoteSummary(
        id = NoteId(path),
        title = title,
        snippet = "",
        isInbox = true
    )
}
