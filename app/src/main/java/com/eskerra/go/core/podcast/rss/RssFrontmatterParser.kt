package com.eskerra.go.core.podcast.rss

/**
 * Parses and rewrites the YAML-ish frontmatter of a `📻` markdown file.
 *
 * Only the fields native RSS sync cares about are interpreted: `rssFeedUrl`
 * (scalar or YAML list), `daysAgo`, and `timeoutMs`. `minFetchIntervalMinutes`
 * is intentionally ignored (parity gap, spec §7.3.1). On a successful refresh the
 * only frontmatter field updated is `rssFetchedAt`; all other lines are preserved
 * verbatim and in order.
 */
object RssFrontmatterParser {

    private const val DELIMITER = "---"
    private const val FETCHED_AT_KEY = "rssFetchedAt"
    private const val FEED_URL_KEY = "rssFeedUrl"
    private const val DAYS_AGO_KEY = "daysAgo"
    private const val TIMEOUT_KEY = "timeoutMs"

    /** A `📻` document split into its frontmatter lines and trailing body. */
    data class Document(
        val frontmatter: RssFrontmatter,
        val frontmatterLines: List<String>,
        val hasFrontmatter: Boolean,
        val body: String
    )

    fun parse(content: String): Document {
        val normalized = content.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        if (lines.firstOrNull()?.trim() != DELIMITER) {
            return Document(
                frontmatter = emptyFrontmatter(),
                frontmatterLines = emptyList(),
                hasFrontmatter = false,
                body = normalized
            )
        }
        val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == DELIMITER }
            ?: return Document(
                frontmatter = emptyFrontmatter(),
                frontmatterLines = emptyList(),
                hasFrontmatter = false,
                body = normalized
            )

        val frontmatterLines = lines.subList(1, closingIndex)
        val body = lines.subList(closingIndex + 1, lines.size).joinToString("\n")
        return Document(
            frontmatter = parseFields(frontmatterLines),
            frontmatterLines = frontmatterLines,
            hasFrontmatter = true,
            body = body.trim('\n')
        )
    }

    /**
     * Returns frontmatter lines with `rssFetchedAt` set to [isoUtc], replacing an
     * existing entry in place or appending a new one at the end.
     */
    fun withFetchedAt(frontmatterLines: List<String>, isoUtc: String): List<String> {
        val newLine = "$FETCHED_AT_KEY: $isoUtc"
        val existingIndex = frontmatterLines.indexOfFirst { keyOf(it) == FETCHED_AT_KEY }
        return if (existingIndex >= 0) {
            frontmatterLines.toMutableList().also { it[existingIndex] = newLine }
        } else {
            frontmatterLines + newLine
        }
    }

    /** Reassembles a full document from frontmatter lines and a body. */
    fun render(frontmatterLines: List<String>, body: String): String {
        val header = buildString {
            append(DELIMITER).append('\n')
            frontmatterLines.forEach { append(it).append('\n') }
            append(DELIMITER).append('\n')
        }
        return if (body.isEmpty()) header else header + "\n" + body.trimEnd('\n') + "\n"
    }

    private fun parseFields(frontmatterLines: List<String>): RssFrontmatter {
        val feedUrls = parseFeedUrls(frontmatterLines)
        val daysAgo = scalarValue(frontmatterLines, DAYS_AGO_KEY)?.toIntOrNull()
            ?.takeIf { it > 0 } ?: RssFrontmatter.DEFAULT_DAYS_AGO
        val timeoutMs = scalarValue(frontmatterLines, TIMEOUT_KEY)?.toLongOrNull()
            ?.takeIf { it > 0 } ?: RssFrontmatter.DEFAULT_TIMEOUT_MS
        return RssFrontmatter(feedUrls = feedUrls, daysAgo = daysAgo, timeoutMs = timeoutMs)
    }

    private fun parseFeedUrls(frontmatterLines: List<String>): List<String> {
        val keyIndex = frontmatterLines.indexOfFirst { keyOf(it) == FEED_URL_KEY }
        if (keyIndex < 0) return emptyList()

        val inlineValue = unquote(frontmatterLines[keyIndex].substringAfter(':').trim())
        if (inlineValue.isNotEmpty()) return listOf(inlineValue)

        val items = mutableListOf<String>()
        var index = keyIndex + 1
        while (index < frontmatterLines.size) {
            val trimmed = frontmatterLines[index].trim()
            if (!trimmed.startsWith("-")) break
            val item = unquote(trimmed.removePrefix("-").trim())
            if (item.isNotEmpty()) items += item
            index++
        }
        return items
    }

    private fun scalarValue(frontmatterLines: List<String>, key: String): String? =
        frontmatterLines.firstOrNull { keyOf(it) == key }
            ?.substringAfter(':')
            ?.trim()
            ?.let(::unquote)
            ?.takeIf { it.isNotEmpty() }

    private fun keyOf(line: String): String? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        return line.substring(0, colon).trim()
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }

    private fun emptyFrontmatter(): RssFrontmatter = RssFrontmatter(
        feedUrls = emptyList(),
        daysAgo = RssFrontmatter.DEFAULT_DAYS_AGO,
        timeoutMs = RssFrontmatter.DEFAULT_TIMEOUT_MS
    )
}
