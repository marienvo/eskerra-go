package com.eskerra.go.core.todayhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatedNoteReadStatusTest {
    @Test
    fun detectsReadStatusOnlyInFirstH1() {
        assertTrue(DatedNoteReadStatus.isRead("# Done ✅\n\nBody"))
        assertFalse(DatedNoteReadStatus.isRead("# Pending\n\n✅"))
        assertFalse(DatedNoteReadStatus.isRead("## Done ✅"))
        assertFalse(DatedNoteReadStatus.isRead("No heading ✅"))
        assertFalse(DatedNoteReadStatus.isRead("---\n# Frontmatter ✅\n---\n# Pending"))
    }

    @Test
    fun markingReadPreservesFrontmatterBodyAndCrLf() {
        val source = "---\r\ntitle: Journal\r\n---\r\n# Today\r\n\r\nBody ✅\r\n"

        val updated = DatedNoteReadStatus.setRead(source, read = true)

        assertEquals("---\r\ntitle: Journal\r\n---\r\n# Today ✅\r\n\r\nBody ✅\r\n", updated)
    }

    @Test
    fun markingUnreadRemovesAllTitleCheckmarksAndTheirSeparatingSpaces() {
        val source = "# Done ✅ and again ✅️\r\n\r\nBody ✅\r\n"

        val updated = DatedNoteReadStatus.setRead(source, read = false)

        assertEquals("# Done and again\r\n\r\nBody ✅\r\n", updated)
    }

    @Test
    fun requestedStateIsAlreadyPresentReturnsIdenticalMarkdown() {
        val unread = "  # Today  \n\nBody"
        val read = "# Today ✅\n"

        assertEquals(unread, DatedNoteReadStatus.setRead(unread, read = false))
        assertEquals(read, DatedNoteReadStatus.setRead(read, read = true))
    }

    @Test
    fun missingTitleFailsClosed() {
        assertNull(DatedNoteReadStatus.setRead("Body only", read = true))
        assertFalse(DatedNoteReadStatus.hasTitle("## Subtitle"))
        assertTrue(DatedNoteReadStatus.hasTitle("# Title"))
    }

    @Test
    fun titleMatchesReaderSummaryText() {
        assertEquals("Today ✅", DatedNoteReadStatus.title("# Today ✅\n\nBody"))
        assertEquals("Today", DatedNoteReadStatus.displayTitle("# Today ✅\n\nBody"))
        assertEquals("Today", DatedNoteReadStatus.displayTitle("# Today"))
    }
}
