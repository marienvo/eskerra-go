package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxSnapshotCodecTest {

    private val note = NoteSummary(
        id = NoteId("Inbox/hello.md"),
        title = "Hello \"world\"",
        snippet = "Line\nbreak",
        isInbox = true,
        lastModifiedEpochMillis = 42L
    )

    @Test
    fun encodeAndDecode_roundTripsSummaries() {
        val fingerprint = GateFingerprint("abc123")
        val raw = InboxSnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            summaries = listOf(note)
        )

        assertEquals(listOf(note), InboxSnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun decode_returnsNullForFingerprintMismatch() {
        val raw = InboxSnapshotCodec.encode(
            fingerprint = GateFingerprint("abc123"),
            savedAtEpochMs = 99L,
            summaries = listOf(note)
        )

        val result = runCatching {
            InboxSnapshotCodec.decode(raw, GateFingerprint("other"))
        }.exceptionOrNull()

        assertEquals("snapshot fingerprint mismatch", result?.message)
    }

    @Test
    fun readFingerprint_rejectsCorruptPayload() {
        val result = runCatching {
            InboxSnapshotCodec.readFingerprint("{not-json")
        }.exceptionOrNull()

        assertEquals("missing workspaceFingerprint", result?.message)
    }

    @Test
    fun decode_roundTripsWhenSnippetContainsObjectDelimiter() {
        val fingerprint = GateFingerprint("abc123")
        val noteWithDelimiter = note.copy(snippet = "prefix},{suffix")
        val raw = InboxSnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            summaries = listOf(noteWithDelimiter)
        )

        assertEquals(listOf(noteWithDelimiter), InboxSnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun decode_roundTripsMultipleSummaries() {
        val fingerprint = GateFingerprint("abc123")
        val second = NoteSummary(
            id = NoteId("Inbox/second.md"),
            title = "Second",
            snippet = "},{not-a-split",
            isInbox = true,
            lastModifiedEpochMillis = 43L
        )
        val raw = InboxSnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            summaries = listOf(note, second)
        )

        assertEquals(listOf(note, second), InboxSnapshotCodec.decode(raw, fingerprint))
    }
}
