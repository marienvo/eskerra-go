package com.eskerra.go.core.markdown

/**
 * Obsidian-style markdown callouts: `> [!type] optional title` (GitHub alerts compatible subset).
 *
 * Mirrors `packages/eskerra-core/src/markdown/callouts.ts`.
 */
object CalloutHeader {

    enum class CalloutColor { BLUE, CYAN, TEAL, GREEN, YELLOW, ORANGE, RED, PURPLE, GREY }

    /** Catalog entry: Material icon ligature, tone color, default label, alternate tokens. */
    data class CatalogEntry(
        val icon: String,
        val color: CalloutColor,
        val label: String,
        val aliases: List<String> = emptyList()
    )

    /** Canonical callout keys (lowercase), preserving declaration order. */
    val CATALOG: Map<String, CatalogEntry> = linkedMapOf(
        "note" to CatalogEntry("edit", CalloutColor.BLUE, "Note"),
        "info" to CatalogEntry("info", CalloutColor.CYAN, "Info"),
        "abstract" to
            CatalogEntry("summarize", CalloutColor.CYAN, "Abstract", listOf("summary", "tldr")),
        "todo" to CatalogEntry("check_circle", CalloutColor.BLUE, "Todo"),
        "tip" to
            CatalogEntry(
                "local_fire_department",
                CalloutColor.TEAL,
                "Tip",
                listOf("hint", "important")
            ),
        "success" to CatalogEntry("check", CalloutColor.GREEN, "Success", listOf("check", "done")),
        "question" to
            CatalogEntry("help_outline", CalloutColor.ORANGE, "Question", listOf("help", "faq")),
        "warning" to
            CatalogEntry("warning", CalloutColor.YELLOW, "Warning", listOf("caution", "attention")),
        "failure" to CatalogEntry("close", CalloutColor.RED, "Failure", listOf("fail", "missing")),
        "danger" to CatalogEntry("bolt", CalloutColor.RED, "Danger", listOf("error")),
        "bug" to CatalogEntry("bug_report", CalloutColor.RED, "Bug"),
        "example" to CatalogEntry("list", CalloutColor.PURPLE, "Example"),
        "quote" to CatalogEntry("format_quote", CalloutColor.GREY, "Quote", listOf("cite"))
    )

    /** Resolved callout metadata for a raw bracket type. */
    data class ResolvedCallout(
        val type: String,
        val icon: String,
        val color: CalloutColor,
        val label: String
    )

    /** Matched callout header on a single line. */
    data class MatchedCalloutHeader(
        val type: String,
        val rawType: String,
        val title: String,
        val startCol: Int,
        val endCol: Int
    )

    private val ALIAS_TO_CANONICAL: Map<String, String> = buildAliasMap()

    private fun buildAliasMap(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for ((canonical, entry) in CATALOG) {
            m[canonical.lowercase()] = canonical
            for (a in entry.aliases) {
                m[a.lowercase()] = canonical
            }
        }
        return m
    }

    /** Resolves a raw bracket type (e.g. `TIP`, `hint`) to catalog metadata; unknown → `note`. */
    fun resolveCallout(rawType: String): ResolvedCallout {
        val key = rawType.trim().lowercase()
        val canonical = ALIAS_TO_CANONICAL[key] ?: "note"
        val entry = CATALOG[canonical] ?: CATALOG.getValue("note")
        return ResolvedCallout(
            type = canonical,
            icon = entry.icon,
            color = entry.color,
            label = entry.label
        )
    }

    private fun isCalloutLineWhitespace(ch: Char): Boolean {
        val c = ch.code
        return c == 9 || c == 10 || c == 11 || c == 12 || c == 13 || c == 32
    }

    private fun isAsciiLetterCode(c: Int): Boolean = (c in 65..90) || (c in 97..122)

    private fun isCalloutTypeTailCode(c: Int): Boolean =
        isAsciiLetterCode(c) || (c in 48..57) || c == 45

    /**
     * Parses the first line of a blockquote for an Obsidian/GitHub-style callout header.
     * Returns null when the line is not a top-level callout header (e.g. `> > [!tip]` is nested).
     */
    fun matchCalloutHeader(lineText: String): MatchedCalloutHeader? {
        val n = lineText.length
        var i = 0
        while (i < n && isCalloutLineWhitespace(lineText[i])) {
            i++
        }
        val quoteBlockStart = i
        while (i < n && lineText[i] == '>') {
            i++
            while (i < n && isCalloutLineWhitespace(lineText[i])) {
                i++
            }
        }
        if (i == quoteBlockStart) {
            return null
        }
        val prefixWithWs = lineText.substring(0, i)
        val quoteOnly = lineText.substring(quoteBlockStart, i)
        if (quoteOnly.count { it == '>' } != 1) {
            return null
        }
        if (i + 3 > n || lineText[i] != '[' || lineText[i + 1] != '!') {
            return null
        }
        var j = i + 2
        val typeStart = j
        if (j >= n || !isAsciiLetterCode(lineText[j].code)) {
            return null
        }
        j++
        while (j < n && isCalloutTypeTailCode(lineText[j].code)) {
            j++
        }
        if (j >= n || lineText[j] != ']') {
            return null
        }
        val rawType = lineText.substring(typeStart, j)
        val bracketToken = lineText.substring(i, j + 1)
        j++
        var foldMarker = ""
        if (j < n && (lineText[j] == '+' || lineText[j] == '-')) {
            foldMarker = lineText[j].toString()
            j++
        }
        val titlePart = lineText.substring(j).trim()
        val resolved = resolveCallout(rawType)
        val tokenStart = prefixWithWs.length
        val tokenEnd = tokenStart + bracketToken.length + foldMarker.length
        return MatchedCalloutHeader(
            type = resolved.type,
            rawType = rawType,
            title = titlePart,
            startCol = tokenStart,
            endCol = tokenEnd
        )
    }
}
