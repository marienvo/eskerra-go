package com.eskerra.go.core.wikilink

import com.eskerra.go.core.model.AmbiguousWikiLink
import com.eskerra.go.core.model.MissingWikiLink
import com.eskerra.go.core.model.MissingWikiLinkReason
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.ResolvedWikiLink
import com.eskerra.go.core.model.WikiLink
import com.eskerra.go.core.model.WikiLinkResolution

/**
 * Resolves parsed wiki links against a [NoteRegistry]. Does not read files or touch
 * repositories.
 */
object WikiLinkResolver {

    fun resolve(link: WikiLink, registry: NoteRegistry): WikiLinkResolution {
        if (!link.hasValidTarget) {
            return MissingWikiLink(link, MissingWikiLinkReason.EmptyTarget)
        }

        val normalizedTarget = normalizeTarget(link.target)
        if (hasPathTraversal(normalizedTarget)) {
            return MissingWikiLink(link, MissingWikiLinkReason.PathTraversal)
        }

        return if (isPathLike(normalizedTarget)) {
            resolveByPath(normalizedTarget, link, registry.notes)
        } else {
            resolveByTitleOrStem(normalizedTarget, link, registry.notes)
        }
    }

    fun resolveAll(links: List<WikiLink>, registry: NoteRegistry): List<WikiLinkResolution> =
        links.map { resolve(it, registry) }

    private fun resolveByPath(
        normalizedTarget: String,
        link: WikiLink,
        notes: List<NoteSummary>
    ): WikiLinkResolution {
        val exactMatches = notes.filter { normalizePath(it.id.value) == normalizedTarget }
        exactMatches.toResolution(link)?.let { return it }

        val extensionlessTarget = dropExtension(normalizedTarget)
        val extensionlessMatches =
            notes.filter { dropExtension(normalizePath(it.id.value)) == extensionlessTarget }
        return extensionlessMatches.toResolution(link)
            ?: MissingWikiLink(link, MissingWikiLinkReason.NoMatch)
    }

    private fun resolveByTitleOrStem(
        target: String,
        link: WikiLink,
        notes: List<NoteSummary>
    ): WikiLinkResolution {
        val matches =
            notes.filter { note ->
                note.title == target || filenameStem(note.id.value) == target
            }.distinctBy { it.id.value }

        return matches.toResolution(link) ?: MissingWikiLink(link, MissingWikiLinkReason.NoMatch)
    }

    private fun List<NoteSummary>.toResolution(link: WikiLink): WikiLinkResolution? = when (size) {
        0 -> null
        1 -> ResolvedWikiLink(link, single())
        else -> AmbiguousWikiLink(link, sortedBy { it.id.value })
    }

    private fun normalizeTarget(raw: String): String = raw.replace('\\', '/')

    private fun normalizePath(path: String): String = path.replace('\\', '/')

    private fun hasPathTraversal(normalizedTarget: String): Boolean {
        if (normalizedTarget.startsWith("/")) {
            return true
        }
        return normalizedTarget.split('/').any { it == ".." }
    }

    private fun isPathLike(target: String): Boolean = target.contains('/') ||
        target.endsWith(".md", ignoreCase = true) ||
        target.endsWith(".markdown", ignoreCase = true)

    private fun dropExtension(path: String): String = when {
        path.endsWith(".markdown", ignoreCase = true) -> path.dropLast(".markdown".length)
        path.endsWith(".md", ignoreCase = true) -> path.dropLast(".md".length)
        else -> path
    }

    private fun filenameStem(notePath: String): String {
        val lastSegment = normalizePath(notePath).substringAfterLast('/')
        return dropExtension(lastSegment)
    }
}
