package com.eskerra.go.core.share

/**
 * Pulls the `<title>` out of an HTML document. There is no HTML library in this project and a
 * page title does not warrant adding one: a first-match regex over the head, with entities
 * decoded, covers what share sources actually send.
 *
 * A `<title>` without a closing tag yields null — the document was cut off mid-title by the read
 * cap, and half a title is worse than none.
 */
object HtmlTitleExtractor {

    private val TITLE = Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    private val NAMED_ENTITIES = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&nbsp;" to " "
    )

    fun extract(html: String): String? {
        val raw = TITLE.find(html)?.groupValues?.get(1) ?: return null
        val decoded = decodeEntities(TAG.replace(raw, ""))
        return decoded
            .map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }
    }

    private fun decodeEntities(text: String): String {
        val named = NAMED_ENTITIES.entries.fold(text) { acc, (entity, replacement) ->
            acc.replace(entity, replacement, ignoreCase = true)
        }
        return NUMERIC_ENTITY.replace(named) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            val codePoint = match.groupValues[2].toIntOrNull(radix)
            if (codePoint == null || codePoint < 0x20 || codePoint > 0x10FFFF) {
                match.value
            } else {
                String(Character.toChars(codePoint))
            }
        }
    }
}
