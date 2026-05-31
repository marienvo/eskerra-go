package com.eskerra.go.core.model

/**
 * In-memory collection of indexed note summaries. Sorted deterministically by
 * relative path for stable list ordering.
 */
data class NoteRegistry(val notes: List<NoteSummary>) {

    val inboxSummaries: List<NoteSummary> get() =
        notes.filter { it.isInbox }
            .sortedWith(
                compareByDescending<NoteSummary> { it.lastModifiedEpochMillis }
                    .thenBy { it.id.value }
            )

    companion object {
        fun fromNotes(unsorted: List<NoteSummary>): NoteRegistry =
            NoteRegistry(unsorted.sortedBy { it.id.value })
    }
}
