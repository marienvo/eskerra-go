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

    fun displayTitle(markdown: String): String? = title(markdown)
        ?.let { DONE_MARKS.replace(it, "").trimEnd() }

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
        var openFence: Fence? = null
        var inHtmlComment = false
        for (
        line in bodyStart.lines
            .take(SCAN_LINE_CAP)
            .takeWhile { it.start - bodyStart.offset < SCAN_CHAR_CAP }
        ) {
            val text = line.text(markdown)
            val trimmed = text.trim()
            val indentation = indentationColumns(text)
            val marker = fenceMarker(trimmed)
            val fence = openFence
            if (fence != null) {
                if (marker != null &&
                    indentation <= 3 &&
                    marker.character == fence.character &&
                    marker.length >= fence.length &&
                    trimmed.length == marker.length
                ) {
                    openFence = null
                }
                continue
            }
            if (inHtmlComment) {
                if (text.contains("-->")) inHtmlComment = false
                continue
            }
            if (trimmed.startsWith("<!--")) {
                inHtmlComment = !text.contains("-->")
                continue
            }
            if (marker != null && indentation <= 3) {
                openFence = marker
                continue
            }
            if (indentation >= 4) continue
            if (trimmed.startsWith("#") &&
                trimmed.getOrNull(1)?.isWhitespace() == true &&
                !trimmed.startsWith("##") &&
                trimmed.drop(1).isNotBlank()
            ) {
                return line
            }
        }
        return null
    }

    private fun indentationColumns(text: String): Int {
        var columns = 0
        for (character in text) {
            when (character) {
                ' ' -> columns++
                '\t' -> columns += 4 - columns % 4
                else -> return columns
            }
        }
        return columns
    }

    private fun fenceMarker(trimmed: String): Fence? {
        val character = trimmed.firstOrNull()
        if (character != '`' && character != '~') return null
        val length = trimmed.takeWhile { it == character }.length
        return if (length >= 3) Fence(character, length) else null
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

    private data class Fence(val character: Char, val length: Int)

    private data class BodyStart(val lines: Sequence<Line>, val offset: Int)
}
