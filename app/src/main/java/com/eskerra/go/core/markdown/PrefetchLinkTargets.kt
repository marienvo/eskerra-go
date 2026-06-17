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
 * never included, and the result is de-duplicated while preserving first-seen order so the closest
 * links are prefetched first.
 */
object PrefetchLinkTargets {

    fun resolve(markdown: String, sourceNoteId: NoteId, registry: NoteRegistry): List<NoteId> {
        val ids = LinkedHashSet<NoteId>()

        WikiLinkParser.parse(markdown).forEach { link ->
            val resolution = WikiLinkResolver.resolve(link, registry)
            if (resolution is ResolvedWikiLink) {
                ids.add(resolution.note.id)
            }
        }

        extractInlineHrefs(markdown).forEach { href ->
            VaultLink.resolveVaultRelativeMarkdownHref(sourceNoteId, href, registry)
                ?.let { ids.add(it) }
        }

        ids.remove(sourceNoteId)
        return ids.toList()
    }

    /**
     * Extracts the href part of inline `[label](href)` markdown links. Drops an optional
     * ` "title"` suffix; does not understand fences or escapes (good enough for prefetch hints).
     */
    private fun extractInlineHrefs(markdown: String): List<String> {
        val hrefs = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < markdown.length) {
            val open = markdown.indexOf("](", searchFrom)
            if (open == -1) break
            val close = markdown.indexOf(')', open + 2)
            if (close == -1) break
            val href = markdown.substring(open + 2, close).trim().substringBefore(' ').trim()
            if (href.isNotEmpty()) hrefs.add(href)
            searchFrom = close + 1
        }
        return hrefs
    }
}
