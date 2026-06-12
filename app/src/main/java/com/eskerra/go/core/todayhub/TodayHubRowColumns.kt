package com.eskerra.go.core.todayhub

/**
 * Today hub row column split/merge (spec §11.3). Mirrors the read path of
 * `packages/eskerra-core/src/todayHub/splitMergeTodayRowColumns.ts`.
 */
object TodayHubRowColumns {

    /** Canonical merge delimiter (blank line required above and below). */
    const val SECTION_DELIMITER = "\n\n::today-section::\n\n"

    /**
     * Paragraph break before the marker (`\n\n` preferred, `\n` allowed), optional horizontal
     * spaces on the marker line (`[ \t]*` only — never `\s*`), then a break after: full blank line,
     * single newline before non-newline content, or end of input. `\z` (not `$`) matches only the
     * true end of input, mirroring JavaScript's `$` without the multiline flag.
     */
    private val SPLIT_RX =
        Regex("""(?:\n\n|\n)[ \t]*::today-section::[ \t]*(?:\n\n|\n(?=[^\n])|\z)""")

    private val SECTION_MARKER_ONLY_LINE = Regex("""^\s*::today-section::\s*$""")

    /** Removes stray `::today-section::`-only lines from a column body. */
    fun stripDelimiterOnlyLines(body: String): String = body.replace("\r\n", "\n")
        .split("\n")
        .filterNot { SECTION_MARKER_ONLY_LINE.matches(it) }
        .joinToString("\n")

    private fun sanitize(chunks: List<String>): List<String> = chunks.map(::stripDelimiterOnlyLines)

    /**
     * Splits a row file body into [columnCount] segments. Single column: the whole text.
     * `columnCount > 1` with no delimiter: segment 0 holds the whole text, the rest are empty.
     * Extra delimited chunks are merged into the last column.
     */
    fun split(fullText: String, columnCount: Int): List<String> {
        require(columnCount >= 1) { "columnCount must be at least 1" }
        val normalized = fullText.replace("\r\n", "\n")
        if (columnCount == 1) {
            return sanitize(listOf(normalized))
        }
        val chunks = SPLIT_RX.split(normalized)
        if (chunks.size == 1) {
            return sanitize(listOf(chunks[0]) + List(columnCount - 1) { "" })
        }
        val head = chunks.take(columnCount - 1)
        val tail = chunks.drop(columnCount - 1).joinToString(SECTION_DELIMITER)
        return sanitize(head + tail)
    }

    fun merge(sections: List<String>): String = when {
        sections.isEmpty() -> ""
        sections.size == 1 -> sections[0]
        else -> sections.joinToString(SECTION_DELIMITER)
    }

    /** True if every section is empty or whitespace-only. */
    fun allBlank(sections: List<String>): Boolean = sections.all { it.isBlank() }
}
