package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId

/**
 * Shared prefetch-target types and ordering. Discovering *which* notes a body links to lives in
 * [PreparedMarkdownLinks] (AST-based, matching the reader's own tap resolution exactly); this
 * object holds only what both prefetch call sites need afterward: the candidate shape and
 * viewport-based prioritization.
 */
object PrefetchLinkTargets {

    /** A prefetch candidate together with where its link token starts in the source. */
    data class Target(val noteId: NoteId, val sourceOffset: Int)

    /**
     * Orders [targets] by distance to the visible source window
     * `[visibleStartOffset, visibleEndOffset]`: a target whose link falls inside the visible window sorts
     * first, then targets are ranked by distance to the nearest edge of that window. Ties keep
     * [targets]' incoming (first-seen) order.
     */
    fun orderByViewport(
        targets: List<Target>,
        visibleStartOffset: Int,
        visibleEndOffset: Int
    ): List<NoteId> {
        if (targets.isEmpty()) return emptyList()

        val visibleStart = minOf(visibleStartOffset, visibleEndOffset)
        val visibleEnd = maxOf(visibleStartOffset, visibleEndOffset)
        fun distanceToVisibleWindow(offset: Int): Int = when {
            offset < visibleStart -> visibleStart - offset
            offset > visibleEnd -> offset - visibleEnd
            else -> 0
        }

        return targets.sortedBy { distanceToVisibleWindow(it.sourceOffset) }.map { it.noteId }
    }
}
