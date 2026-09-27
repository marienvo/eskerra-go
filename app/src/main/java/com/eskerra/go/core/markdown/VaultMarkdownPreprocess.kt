package com.eskerra.go.core.markdown

/**
 * Pure preprocessing for the read-only vault markdown renderer (spec §8.1).
 *
 * Mirrors `packages/eskerra-core/src/markdown/splitYamlFrontmatter.ts` and
 * `apps/mobile/src/features/vault/markdown/vaultWikiLinkPreprocess.ts`.
 */
object VaultMarkdownPreprocess {

    /** Synthetic scheme consumed by the vault readonly markdown link rules (not a real URL). */
    const val VAULT_READONLY_WIKI_LINK_SCHEME = "eskerra-wiki:"

    private val WIKI_LINK_RE = Regex("""\[\[([^\[\]]+)\]\]""")
    private val FENCE_RE = Regex("""```[\s\S]*?```""")

    /** Result of [splitYamlFrontmatter]. */
    data class FrontmatterSplit(
        val frontmatter: String?,
        val body: String,
        /** Normalized text before the opening `---` line (non-empty only when not the first line). */
        val leadingBeforeFrontmatter: String
    )

    /**
     * Detects a well-formed YAML frontmatter block at the start of [markdown] (after optional
     * blank lines): first non-empty line is exactly `---`, later a closing line is exactly `---`.
     * If not well-formed (including a missing closing delimiter), returns `frontmatter = null` and
     * `body` is the full normalized text unchanged.
     */
    fun splitYamlFrontmatter(markdown: String): FrontmatterSplit {
        val normalized = markdown.replace("\r\n", "\n")
        val lines = normalized.split("\n")

        var i = 0
        while (i < lines.size && lines[i].trim().isEmpty()) {
            i += 1
        }

        if (i >= lines.size || lines[i].trim() != "---") {
            return FrontmatterSplit(
                frontmatter = null,
                body = normalized,
                leadingBeforeFrontmatter = ""
            )
        }

        val openLine = i
        var off = 0
        for (j in 0 until openLine) {
            off += lines[j].length + 1
        }
        val leadingBeforeFrontmatter = normalized.substring(0, off)

        i = openLine + 1
        while (i < lines.size && lines[i].trim() != "---") {
            i += 1
        }

        if (i >= lines.size) {
            return FrontmatterSplit(
                frontmatter = null,
                body = normalized,
                leadingBeforeFrontmatter = ""
            )
        }

        val closeLine = i
        val frontmatter = lines.subList(openLine, closeLine + 1).joinToString("\n")
        val body = lines.subList(closeLine + 1, lines.size).joinToString("\n")
        return FrontmatterSplit(frontmatter, body, leadingBeforeFrontmatter)
    }

    /** Applies [transform] only to segments outside ` ``` `-fenced blocks (non-overlapping, greedy). */
    fun transformOutsideTripleBacktickFences(
        markdown: String,
        transform: (String) -> String
    ): String {
        val out = StringBuilder()
        var last = 0
        for (match in FENCE_RE.findAll(markdown)) {
            out.append(transform(markdown.substring(last, match.range.first)))
            out.append(match.value)
            last = match.range.last + 1
        }
        out.append(transform(markdown.substring(last)))
        return out.toString()
    }

    /** Rewrites `[[inner]]` to a markdown inline link so the parser produces a link node. */
    fun wikiLinksToSyntheticMarkdownLinks(chunk: String): String =
        WIKI_LINK_RE.replace(chunk) { match ->
            val inner = match.groupValues[1]
            val label = escapeMarkdownLinkText(wikiLinkMarkdownLabel(inner))
            val href = VAULT_READONLY_WIKI_LINK_SCHEME + encodeUriComponent(inner.trim())
            "[$label]($href)"
        }

    /** Full §8.1 step 3: rewrite wiki links outside fenced code only. */
    fun preprocessVaultReadonlyMarkdownBody(markdown: String): String =
        transformOutsideTripleBacktickFences(markdown, ::wikiLinksToSyntheticMarkdownLinks)

    /**
     * Removes the leading `# ` heading line that the note reader already renders as its title
     * (mirrors `MarkdownNoteScanner`'s title extraction: the first line whose trimmed form starts
     * with `# `), plus any blank lines directly following it. Returns [markdown] unchanged when no
     * such line exists, since the title then came from the filename rather than the body.
     */
    fun stripTitleHeading(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        val titleIndex = lines.indexOfFirst(::isTitleHeadingLine)
        if (titleIndex < 0) return markdown

        var bodyStart = titleIndex + 1
        while (bodyStart < lines.size && lines[bodyStart].isBlank()) {
            bodyStart += 1
        }
        return (lines.subList(0, titleIndex) + lines.subList(bodyStart, lines.size))
            .joinToString("\n")
    }

    private fun isTitleHeadingLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith(H1_LINE_PREFIX) && trimmed.length > H1_LINE_PREFIX.length
    }

    private fun wikiLinkMarkdownLabel(inner: String): String {
        val raw = inner.trim()
        val pipeAt = raw.indexOf('|')
        if (pipeAt < 0) {
            return raw
        }
        val display = raw.substring(pipeAt + 1).trim()
        return if (display.isEmpty()) raw.substring(0, pipeAt).trim() else display
    }

    private fun escapeMarkdownLinkText(text: String): String =
        text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")

    /** Matches JavaScript `encodeURIComponent`: unreserved set `A-Za-z0-9-_.!~*'()` pass through. */
    private fun encodeUriComponent(value: String): String {
        val out = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt().toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in UNRESERVED) {
                out.append(ch)
            } else {
                out.append('%')
                out.append("0123456789ABCDEF"[(byte.toInt() ushr 4) and 0xF])
                out.append("0123456789ABCDEF"[byte.toInt() and 0xF])
            }
        }
        return out.toString()
    }

    private const val UNRESERVED = "-_.!~*'()"
    private const val H1_LINE_PREFIX = "# "
}
