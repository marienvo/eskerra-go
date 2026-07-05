package com.eskerra.go.core.markdown

/**
 * Today Hub intro preprocessing: remove the note's title H1 from the body when it duplicates the
 * shell header (spec §11). Only strips an ATX H1 that matches [title] and appears before any other
 * non-blank, unfenced content.
 */
object TodayHubIntroStrip {

    fun stripLeadingAtxH1ForTitle(markdown: String, title: String?): String {
        if (title.isNullOrBlank()) {
            return markdown
        }

        val lines = markdown.lines()
        var activeFence: MarkdownFenceTracker.OpenFence? = null
        for (i in lines.indices) {
            val line = lines[i]
            if (activeFence != null) {
                if (MarkdownFenceTracker.isClosing(line, activeFence)) {
                    activeFence = null
                }
                continue
            }

            val opening = MarkdownFenceTracker.parseOpening(line)
            if (opening != null) {
                activeFence = opening
                continue
            }

            if (line.trim().isEmpty()) {
                continue
            }

            if (isAtxH1ForTitle(line, title)) {
                if (lines.take(i).any { it.trim().isNotEmpty() }) {
                    return markdown
                }
                val remaining = lines.toMutableList().apply { removeAt(i) }
                if (i < remaining.size && remaining[i].isBlank()) {
                    remaining.removeAt(i)
                }
                return remaining.joinToString("\n").trimStart('\n')
            }
            return markdown
        }
        return markdown
    }

    internal fun isAtxH1ForTitle(line: String, title: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith(H1_PREFIX)) {
            return false
        }
        val headingText = trimmed
            .removePrefix(H1_PREFIX)
            .trim()
            .replace(TRAILING_CLOSING_HASHES, "")
            .trim()
        return headingText == title
    }

    private const val H1_PREFIX = "# "
    private val TRAILING_CLOSING_HASHES = Regex("""\s+#+$""")
}
