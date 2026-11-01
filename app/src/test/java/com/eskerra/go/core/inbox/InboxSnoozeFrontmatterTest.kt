package com.eskerra.go.core.inbox

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxSnoozeFrontmatterTest {
    @Test
    fun readsQuotedTimestampAndPreservesOffset() {
        val markdown = "\uFEFF---\r\nsnoozed_until: \"2026-11-02T09:00:00+01:00\"\r\n---\r\nBody"
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), InboxSnoozeFrontmatter.read(markdown))
    }

    @Test
    fun readsFrontmatterAfterBlankLinesLikeDesktop() {
        val markdown = "\n\n---\nsnoozed_until: 2026-11-02T08:00:00Z\n---\nBody"
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), InboxSnoozeFrontmatter.read(markdown))
    }

    @Test
    fun malformedDuplicateOrMissingFrontmatterDoesNotHideNote() {
        assertNull(InboxSnoozeFrontmatter.read("---\nsnoozed_until: nope\n---\n"))
        assertNull(InboxSnoozeFrontmatter.read("---\nsnoozed_until: x\nsnoozed_until: y\n---\n"))
        assertNull(InboxSnoozeFrontmatter.read("---\nsnoozed_until: 2026-11-02T08:00:00Z\n"))
        assertNull(InboxSnoozeFrontmatter.read("Body\nsnoozed_until: 2026-11-02T08:00:00Z"))
    }
}
