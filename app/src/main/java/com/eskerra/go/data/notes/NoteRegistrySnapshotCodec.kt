package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteRegistry

/** JSON codec for the full note-registry snapshot file (`note_registry_snapshot.json`). */
internal object NoteRegistrySnapshotCodec {

    private const val NOTES_ARRAY_KEY = "notes"

    fun encode(fingerprint: GateFingerprint, savedAtEpochMs: Long, registry: NoteRegistry): String =
        SnapshotNoteJsonCodec.encodeEnvelope(
            fingerprint = fingerprint,
            savedAtEpochMs = savedAtEpochMs,
            notesArrayKey = NOTES_ARRAY_KEY,
            notes = registry.notes
        )

    fun readFingerprint(raw: String): GateFingerprint = SnapshotNoteJsonCodec.readFingerprint(raw)

    fun decode(raw: String, expectedFingerprint: GateFingerprint): NoteRegistry =
        NoteRegistry.fromNotes(
            SnapshotNoteJsonCodec.decodeNotesArray(
                raw = raw,
                expectedFingerprint = expectedFingerprint,
                notesArrayKey = NOTES_ARRAY_KEY
            )
        )
}
