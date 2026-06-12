package com.eskerra.go.core.model

/**
 * Reader presentation for a loaded note: the note summary, its content, and the vault registry the
 * renderer resolves wiki / internal links against (spec §8). The plaintext segment model was
 * retired in Phase 3 in favour of in-renderer link annotation.
 */
data class NoteReaderDocument(
    val note: NoteSummary,
    val content: NoteContent,
    val registry: NoteRegistry
)
