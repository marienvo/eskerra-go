package com.eskerra.go.core.model

/**
 * Lightweight view of a note for list rendering. Holds just enough to show an
 * inbox row and open the full note.
 */
data class NoteSummary(
    val id: NoteId,
    val title: String,
    val snippet: String,
    val isInbox: Boolean,
    val lastModifiedEpochMillis: Long = 0L
)
