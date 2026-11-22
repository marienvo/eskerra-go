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
    fun fencedCodeExamplesAreNeverMutatedAsTitles() {
        val source = "```md\n# Example\n```\n\n# Today\n\nBody"

        assertEquals(
            "```md\n# Example\n```\n\n# Today ✅\n\nBody",
            DatedNoteReadStatus.setRead(source, read = true)
        )
        assertNull(DatedNoteReadStatus.setRead("~~~\n# Example\n~~~", read = true))
    }

    @Test
    fun indentedCodeExamplesAreNotTitles() {
        val source = "    # Example\n\t# Also code\n# Today"

        assertEquals(
            "    # Example\n\t# Also code\n# Today ✅",
            DatedNoteReadStatus.setRead(source, true)
        )
        assertNull(DatedNoteReadStatus.setRead("    # Example", read = true))
    }

    @Test
    fun titleMatchesReaderSummaryText() {
        assertEquals("Today ✅", DatedNoteReadStatus.title("# Today ✅\n\nBody"))
        assertEquals("Today", DatedNoteReadStatus.displayTitle("# Today ✅\n\nBody"))
        assertEquals("Today", DatedNoteReadStatus.displayTitle("# Today"))
    }
}
