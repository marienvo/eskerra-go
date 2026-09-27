package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.ResolvedWikiLink
import com.eskerra.go.core.wikilink.WikiLinkParser
import com.eskerra.go.core.wikilink.WikiLinkResolver

/**
 * Pure collection of prefetch candidates from a note's markdown: unambiguous `[[wikilinks]]` and
 * relative `.md` inline links that resolve to exactly one note in [registry].
 *
 * Ambiguous and missing links are skipped — there is no single file to warm. The source note is
 * never included, and the result is de-duplicated while preserving first-seen order (of the target
 * closest to the top of the note, since that is the order links are found in).
 */
object PrefetchLinkTargets {

    /** A prefetch candidate together with where its link token starts in the source markdown. */
    data class Target(val noteId: NoteId, val sourceOffset: Int)

    fun resolve(markdown: String, sourceNoteId: NoteId, registry: NoteRegistry): List<NoteId> =
        resolveWithOffsets(markdown, sourceNoteId, registry).map { it.noteId }

    /** Same candidates as [resolve], each paired with its first-seen link's start offset. */
    fun resolveWithOffsets(
        markdown: String,
        sourceNoteId: NoteId,
        registry: NoteRegistry
    ): List<Target> {
        val offsetByNoteId = LinkedHashMap<NoteId, Int>()

        WikiLinkParser.parse(markdown).forEach { link ->
            val resolution = WikiLinkResolver.resolve(link, registry)
            if (resolution is ResolvedWikiLink) {
                offsetByNoteId.putIfAbsent(resolution.note.id, link.sourceRange.first)
            }
        }

        extractInlineHrefs(markdown).forEach { (href, offset) ->
            VaultLink.resolveVaultRelativeMarkdownHref(sourceNoteId, href, registry)
                ?.let { offsetByNoteId.putIfAbsent(it, offset) }
        }

        offsetByNoteId.remove(sourceNoteId)
        return offsetByNoteId.map { (noteId, offset) -> Target(noteId, offset) }
    }

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

    /**
     * Extracts the href part of inline `[label](href)` markdown links, paired with the offset of the
     * link's opening `[`. Drops an optional ` "title"` suffix; does not understand fences or escapes
     * (good enough for prefetch hints).
     */
    private fun extractInlineHrefs(markdown: String): List<Pair<String, Int>> {
        val hrefs = mutableListOf<Pair<String, Int>>()
        var searchFrom = 0
        while (searchFrom < markdown.length) {
            val open = markdown.indexOf("](", searchFrom)
            if (open == -1) break
            val close = markdown.indexOf(')', open + 2)
            if (close == -1) break
            val href = markdown.substring(open + 2, close).trim().substringBefore(' ').trim()
            if (href.isNotEmpty()) {
                val labelStart = markdown.lastIndexOf('[', open).takeIf { it >= 0 } ?: open
                hrefs.add(href to labelStart)
            }
            searchFrom = close + 1
        }
        return hrefs
    }
}
