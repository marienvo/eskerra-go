package com.eskerra.go.core.inbox

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxSnoozePatchTest {
    private val until = Instant.parse("2026-11-02T08:00:00Z")

    @Test
    fun preservesExistingBytesOutsideOwnedField() {
        val before = "\uFEFF---\r\ntitle: Hello\r\n# Comment\r\n---\r\nBody\r\n"
        val after = "\uFEFF---\r\ntitle: Hello\r\n# Comment\r\nsnoozed_until: " +
            "\"2026-11-02T08:00:00Z\"\r\n---\r\nBody\r\n"
        assertEquals(after, InboxSnoozePatch.apply(before, until).getOrThrow())
    }

    @Test
    fun addsFrontmatterAndRejectsUnsafeShapes() {
        assertEquals(
            "---\nsnoozed_until: \"2026-11-02T08:00:00Z\"\n---\nBody",
            InboxSnoozePatch.apply("Body", until).getOrThrow()
        )
        assertTrue(InboxSnoozePatch.apply("---\ntags:\n  - one\n---\nBody", until).isFailure)
        assertTrue(InboxSnoozePatch.apply("---\na: 1\na: 2\n---\nBody", until).isFailure)
        assertTrue(InboxSnoozePatch.apply("---\na: 1\nBody", until).isFailure)
    }
}
