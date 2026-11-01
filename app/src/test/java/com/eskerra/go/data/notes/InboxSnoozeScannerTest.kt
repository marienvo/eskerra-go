package com.eskerra.go.data.notes

import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InboxSnoozeScannerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsInboxMetadataAndReusesItOnLaterScans() {
        val root = temp.newFolder()
        File(root, "Inbox").mkdirs()
        File(root, "Inbox/note.md").writeText(
            "---\nsnoozed_until: \"2026-11-02T08:00:00Z\"\n---\n# Note\n"
        )
        val scanner = MarkdownNoteScanner()
        val first = scanner.scan(root).getOrThrow()
        val expected = Instant.parse("2026-11-02T08:00:00Z").toEpochMilli()
        assertEquals(expected, first.inboxSummaries.single().snoozedUntilEpochMillis)
        val second = scanner.scan(root, first).getOrThrow()
        assertEquals(expected, second.inboxSummaries.single().snoozedUntilEpochMillis)
    }
}
