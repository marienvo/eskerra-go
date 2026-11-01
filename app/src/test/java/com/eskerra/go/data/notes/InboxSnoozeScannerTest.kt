package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
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
        val fingerprint = GateFingerprint("test")
        val snapshot = NoteRegistrySnapshotCodec.encode(fingerprint, 0L, first)
        val restored = NoteRegistrySnapshotCodec.decode(snapshot, fingerprint)
        assertEquals(
            first.inboxSummaries.single().inboxContentSha256,
            restored.inboxSummaries.single().inboxContentSha256
        )
        val second = scanner.scan(root, restored).getOrThrow()
        assertEquals(expected, second.inboxSummaries.single().snoozedUntilEpochMillis)
    }
}
