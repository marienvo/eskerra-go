package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRegistrySnapshotCodecTest {

    private val inboxNote = NoteSummary(
        id = NoteId("Inbox/hello.md"),
        title = "Hello \"world\"",
        snippet = "Line\nbreak",
        isInbox = true,
        lastModifiedEpochMillis = 42L
    )

    private val vaultNote = NoteSummary(
        id = NoteId("Notes/other.md"),
        title = "Other",
        snippet = "Vault body",
        isInbox = false,
        lastModifiedEpochMillis = 43L
    )

    @Test
    fun encodeAndDecode_roundTripsEmptyRegistry() {
        val fingerprint = GateFingerprint("abc123")
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            registry = NoteRegistry.fromNotes(emptyList())
        )

        assertEquals(
            NoteRegistry.fromNotes(emptyList()),
            NoteRegistrySnapshotCodec.decode(raw, fingerprint)
        )
    }

    @Test
    fun encodeAndDecode_roundTripsNotes() {
        val fingerprint = GateFingerprint("abc123")
        val registry = NoteRegistry.fromNotes(listOf(inboxNote, vaultNote))
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            registry = registry
        )

        assertEquals(registry, NoteRegistrySnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun decode_returnsNullForFingerprintMismatch() {
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = GateFingerprint("abc123"),
            savedAtEpochMs = 99L,
            registry = NoteRegistry.fromNotes(listOf(inboxNote))
        )

        val result = runCatching {
            NoteRegistrySnapshotCodec.decode(raw, GateFingerprint("other"))
        }.exceptionOrNull()

        assertEquals("snapshot fingerprint mismatch", result?.message)
    }

    @Test
    fun readFingerprint_rejectsCorruptPayload() {
        val result = runCatching {
            NoteRegistrySnapshotCodec.readFingerprint("{not-json")
        }.exceptionOrNull()

        assertEquals("missing workspaceFingerprint", result?.message)
    }

    @Test
    fun decode_roundTripsWhenSnippetContainsObjectDelimiter() {
        val fingerprint = GateFingerprint("abc123")
        val noteWithDelimiter = inboxNote.copy(snippet = "prefix},{suffix")
        val registry = NoteRegistry.fromNotes(listOf(noteWithDelimiter))
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            registry = registry
        )

        assertEquals(registry, NoteRegistrySnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun encodeAndDecode_roundTripsSizeBytes() {
        val fingerprint = GateFingerprint("abc123")
        val note = inboxNote.copy(sizeBytes = 1234L)
        val registry = NoteRegistry.fromNotes(listOf(note))
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            registry = registry
        )

        assertEquals(registry, NoteRegistrySnapshotCodec.decode(raw, fingerprint))
        assertEquals(true, raw.contains("\"sizeBytes\":1234"))
    }

    @Test
    fun decode_legacySnapshotWithoutSizeBytes_defaultsToZero() {
        val fingerprint = GateFingerprint("abc123")
        val legacyRaw =
            """
            {"workspaceFingerprint":"abc123","savedAtEpochMs":99,"notes":[
            {"id":"Inbox/hello.md","title":"Hello \"world\"","snippet":"Line\nbreak","isInbox":true,"lastModifiedEpochMillis":42}
            ]}
            """.trimIndent()

        val decoded = NoteRegistrySnapshotCodec.decode(legacyRaw, fingerprint)

        assertEquals(0L, decoded.notes.single().sizeBytes)
        assertEquals(inboxNote, decoded.notes.single())
    }

    @Test
    fun encode_usesNotesArrayKey_notSummaries() {
        val fingerprint = GateFingerprint("abc123")
        val raw = NoteRegistrySnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            registry = NoteRegistry.fromNotes(listOf(inboxNote))
        )

        assertEquals(true, raw.contains("\"notes\":["))
        assertEquals(false, raw.contains("\"summaries\":["))
    }
}
