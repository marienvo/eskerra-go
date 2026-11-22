package com.eskerra.go.core.todayhub

/** Read-status detection and byte-preserving title mutations for dated notes. */
object DatedNoteReadStatus {
    private const val DONE_MARK = "✅"
    private const val VARIATION_SELECTOR = '\uFE0F'
    private const val SCAN_LINE_CAP = 60
    private const val SCAN_CHAR_CAP = 4000
    private val DONE_MARKS = Regex(" ?✅\uFE0F?")

    fun hasTitle(markdown: String): Boolean = firstTitleLine(markdown) != null

    fun isRead(markdown: String): Boolean =
        firstTitleLine(markdown)?.text(markdown)?.contains(DONE_MARK) == true

    fun title(markdown: String): String? = firstTitleLine(markdown)
        ?.text(markdown)
        ?.trim()
        ?.removePrefix("#")
        ?.trim()

    /** Returns null without a first H1, and otherwise changes only that source line. */
    fun setRead(markdown: String, read: Boolean): String? {
        val line = firstTitleLine(markdown) ?: return null
        val source = line.text(markdown)
        if (read == source.contains(DONE_MARK)) return markdown

        if (read) return markdown.replaceRange(line.contentEnd, line.contentEnd, " $DONE_MARK")

        val updated = DONE_MARKS.replace(source, "")
        return markdown.replaceRange(line.start, line.contentEnd, updated)
    }

    private fun firstTitleLine(markdown: String): Line? {
        val bodyStart = bodyStartLine(markdown)
        return bodyStart.lines
            .take(SCAN_LINE_CAP)
            .takeWhile { it.start - bodyStart.offset < SCAN_CHAR_CAP }
            .firstOrNull { line ->
                val trimmed = line.text(markdown).trim()
                trimmed.startsWith("#") && !trimmed.startsWith("##") && trimmed.drop(1).isNotBlank()
            }
    }

    private fun bodyStartLine(markdown: String): BodyStart {
        val lines = lines(markdown)
        val opening = lines.firstOrNull { it.text(markdown).isNotBlank() }
            ?: return BodyStart(lines, 0)
        if (opening.text(markdown).trim() != "---") return BodyStart(lines, 0)

        val closing = lines.firstOrNull {
            it.start > opening.start && it.text(markdown).trim() == "---"
        } ?: return BodyStart(lines, 0)
        val body = lines.dropWhile { it.start <= closing.start }
        return BodyStart(body, closing.nextStart)
    }

    private fun lines(markdown: String): Sequence<Line> = sequence {
        var start = 0
        while (start <= markdown.length) {
            val newline = markdown.indexOf('\n', start).let { if (it < 0) markdown.length else it }
            val contentEnd = if (newline > start && markdown[newline - 1] == '\r') {
                newline - 1
            } else {
                newline
            }
            yield(Line(start, contentEnd, if (newline < markdown.length) newline + 1 else newline))
            if (newline == markdown.length) break
            start = newline + 1
        }
    }

    private data class Line(val start: Int, val contentEnd: Int, val nextStart: Int) {
        fun text(markdown: String): String = markdown.substring(start, contentEnd)
    }

    private data class BodyStart(val lines: Sequence<Line>, val offset: Int)
}
