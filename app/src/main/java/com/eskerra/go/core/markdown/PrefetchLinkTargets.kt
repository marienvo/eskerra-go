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
     * Orders [targets] by distance to the visible window `[visibleStartFraction, visibleEndFraction]`
     * of a [markdownLength]-character body: a target whose link falls inside the visible window sorts
     * first, then targets are ranked by distance to the nearest edge of that window. Ties keep
     * [targets]' incoming (first-seen) order. Falls back to that incoming order when [markdownLength]
     * is not positive.
     */
    fun orderByViewport(
        targets: List<Target>,
        markdownLength: Int,
        visibleStartFraction: Float,
        visibleEndFraction: Float
    ): List<NoteId> {
        if (markdownLength <= 0 || targets.isEmpty()) return targets.map { it.noteId }

        val visibleStart = (visibleStartFraction.coerceIn(0f, 1f) * markdownLength)
        val visibleEnd = (visibleEndFraction.coerceIn(0f, 1f) * markdownLength)
        fun distanceToVisibleWindow(offset: Int): Float = when {
            offset < visibleStart -> visibleStart - offset
            offset > visibleEnd -> offset - visibleEnd
            else -> 0f
        }

        return targets.sortedBy { distanceToVisibleWindow(it.sourceOffset) }.map { it.noteId }
    }
}
