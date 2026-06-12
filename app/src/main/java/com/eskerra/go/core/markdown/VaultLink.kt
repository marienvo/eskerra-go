package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.vault.VaultVisibility
import java.net.URLDecoder

/**
 * Vault-relative markdown link resolution for read-only rendering (spec §9.3).
 *
 * Mirrors `packages/eskerra-core/src/vaultRelativeMarkdownLink.ts`, adapted for eskerra-go's
 * plain filesystem vault paths (no SAF content:// URIs). NoteId values are vault-relative paths
 * such as `Inbox/my-note.md`; the registry is the authority for valid targets.
 *
 * Resolution rules:
 * - `./name.md`, `../dir/name.md`, bare `dir/name.md` → resolved against source note's directory
 * - Absolute `/` paths → not supported (returned as unresolvable)
 * - Non-markdown paths, external URLs, empty hrefs → null
 * - Target directory segment excluded by [VaultVisibility] → null
 * - Target not found in [NoteRegistry] → null
 */
object VaultLink {

    private val BROWSER_OPENABLE_SCHEMES = setOf("http", "https", "mailto")

    /** Strips query string and fragment from a raw markdown href, then trims whitespace. */
    fun stripMarkdownLinkHrefToPathPart(raw: String): String {
        var s = raw.trim()
        val q = s.indexOf('?')
        if (q >= 0) s = s.substring(0, q).trimEnd()
        val h = s.indexOf('#')
        if (h >= 0) s = s.substring(0, h).trimEnd()
        return s.trim()
    }

    /**
     * True when `href` uses a URL scheme or is protocol-relative (`//`).
     *
     * Scheme detection: a `:` preceded only by ASCII letters is treated as a scheme separator.
     * This correctly excludes `./note.md` (`.` is not a letter) and `eskerra-wiki:` (contains `-`
     * which is not a letter, but those hrefs are handled by [VaultReadonlyLink] before this runs).
     */
    fun isExternalMarkdownHref(href: String): Boolean {
        val h = href.trim()
        if (h.isEmpty() || h.startsWith("//")) return true
        val colon = h.indexOf(':')
        if (colon <= 0) return false
        return h.substring(0, colon).all { it.isLetter() }
    }

    /**
     * True when `href` may be opened in the system browser: `http`, `https`, or `mailto` only.
     * Protocol-relative `//` and other schemes (e.g. `javascript:`, `file:`) are excluded.
     */
    fun isBrowserOpenableMarkdownHref(href: String): Boolean {
        val h = href.trim()
        if (h.isEmpty()) return false
        val colon = h.indexOf(':')
        if (colon <= 0) return false
        val scheme = h.substring(0, colon)
        if (!scheme.all { it.isLetter() }) return false
        return scheme.lowercase() in BROWSER_OPENABLE_SCHEMES
    }

    /**
     * Resolves `rel` against `baseDirPath` using POSIX rules (`.` and `..` navigation).
     * Both paths use forward slashes; percent-encoded segments in `rel` are decoded.
     * Attempts to pop past the root are silently clamped (the stack never goes negative).
     *
     * Returns a vault-relative path without a leading slash.
     */
    fun posixResolveRelativeToDirectory(baseDirPath: String, rel: String): String {
        val decodedRel = try {
            URLDecoder.decode(rel, Charsets.UTF_8.name())
        } catch (_: Exception) {
            rel
        }
        val baseParts = baseDirPath.split('/').filter { it.isNotEmpty() }
        val relParts = decodedRel.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = baseParts.toMutableList()
        for (part in relParts) {
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else {
                stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    /**
     * Resolves a relative inline-markdown `href` against `sourceNoteId` to a vault-relative
     * [NoteId], or `null` when the target cannot be resolved.
     *
     * Returns null when:
     * - The href is blank, external, or does not end in `.md`
     * - The href is absolute (starts with `/`) — not supported in this vault layout
     * - Any directory segment of the resolved path is excluded by [VaultVisibility]
     * - No note with that vault-relative path exists in [registry] (case-insensitive)
     */
    fun resolveVaultRelativeMarkdownHref(
        sourceNoteId: NoteId,
        rawHref: String,
        registry: NoteRegistry
    ): NoteId? {
        val pathPart = stripMarkdownLinkHrefToPathPart(rawHref)
        if (pathPart.isEmpty() || isExternalMarkdownHref(pathPart)) return null
        if (!pathPart.lowercase().endsWith(".md")) return null
        if (pathPart.startsWith('/')) return null

        val sourceDir = sourceNoteId.value.substringBeforeLast('/', "")
        val resolved = posixResolveRelativeToDirectory(sourceDir, pathPart)
        if (resolved.isEmpty() || !resolved.lowercase().endsWith(".md")) return null

        // Reject if any directory segment is excluded
        val segments = resolved.split('/')
        for (i in 0 until segments.size - 1) {
            if (VaultVisibility.isExcludedDirectorySegment(segments[i])) return null
        }

        return registry.notes.firstOrNull { it.id.value.equals(resolved, ignoreCase = true) }?.id
    }
}
