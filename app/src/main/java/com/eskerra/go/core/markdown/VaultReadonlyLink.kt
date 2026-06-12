package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.AmbiguousWikiLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.ResolvedWikiLink
import com.eskerra.go.core.wikilink.WikiLinkParser
import com.eskerra.go.core.wikilink.WikiLinkResolver
import java.net.URLDecoder

/**
 * Pure classification of vault readonly markdown links into tone (spec §8.3) and tap target.
 *
 * Mirrors the link-colour decision in
 * `apps/mobile/src/features/vault/markdown/vaultReadonlyMarkdownRules.tsx`. Relative `.md` href
 * resolution (`./`, `../`, vault-root rules) is deferred to Phase 4; here a non-wiki, non-external
 * href is treated optimistically while the index is not ready and muted once it is.
 */
object VaultReadonlyLink {

    /** Vault markdown index readiness for wiki / relative link resolution. */
    enum class IndexStatus { LOADING, READY, ERROR }

    /** Link colour bucket (spec §8.3). */
    enum class LinkTone { INTERNAL, EXTERNAL, MUTED }

    /** What a tapped link resolves to. */
    sealed interface LinkTarget {
        data class Internal(val noteId: NoteId) : LinkTarget

        data class External(val url: String) : LinkTarget

        data class Ambiguous(val candidates: List<NoteId>, val inner: String) : LinkTarget

        data object Unresolved : LinkTarget
    }

    /** Decodes a synthetic `eskerra-wiki:` href back to its inner `[[...]]` text, or null. */
    fun decodeWikiHref(href: String): String? {
        if (!href.startsWith(VaultMarkdownPreprocess.VAULT_READONLY_WIKI_LINK_SCHEME)) {
            return null
        }
        val raw = href.substring(VaultMarkdownPreprocess.VAULT_READONLY_WIKI_LINK_SCHEME.length)
        return try {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** True for browser-openable schemes (http, https, mailto). */
    fun isExternalHref(href: String): Boolean {
        val lower = href.trim().lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("mailto:")
    }

    /** Resolves a decoded wiki inner (`target` or `target|alias`) against the registry. */
    fun resolveWikiInner(inner: String, registry: NoteRegistry) =
        WikiLinkParser.parse("[[$inner]]").firstOrNull()?.let {
            WikiLinkResolver.resolve(it, registry)
        }

    /** Colour bucket for a link href under the current index [status] (spec §8.3). */
    fun toneFor(href: String, registry: NoteRegistry, status: IndexStatus): LinkTone {
        val inner = decodeWikiHref(href)
        if (inner != null) {
            if (isExternalHref(inner)) {
                return LinkTone.EXTERNAL
            }
            return when (resolveWikiInner(inner, registry)) {
                is ResolvedWikiLink, is AmbiguousWikiLink -> LinkTone.INTERNAL
                else -> if (status != IndexStatus.READY) LinkTone.INTERNAL else LinkTone.MUTED
            }
        }
        if (isExternalHref(href)) {
            return LinkTone.EXTERNAL
        }
        return if (status != IndexStatus.READY) LinkTone.INTERNAL else LinkTone.MUTED
    }

    /** Resolves a tapped link href to a navigation target. */
    fun targetFor(href: String, registry: NoteRegistry): LinkTarget {
        val inner = decodeWikiHref(href)
        if (inner != null) {
            if (isExternalHref(inner)) {
                return LinkTarget.External(inner.trim())
            }
            return when (val res = resolveWikiInner(inner, registry)) {
                is ResolvedWikiLink -> LinkTarget.Internal(res.note.id)
                is AmbiguousWikiLink ->
                    LinkTarget.Ambiguous(res.candidates.map { it.id }, inner)
                else -> LinkTarget.Unresolved
            }
        }
        if (isExternalHref(href)) {
            return LinkTarget.External(href.trim())
        }
        return LinkTarget.Unresolved
    }
}
