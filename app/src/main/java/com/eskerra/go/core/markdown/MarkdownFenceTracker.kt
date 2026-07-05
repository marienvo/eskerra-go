package com.eskerra.go.core.markdown

/**
 * CommonMark §4.5 code-fence line detection for line-scanners that must skip fenced content.
 *
 * Supports backtick and tilde fences, opening/closing length rules, and up to three spaces of indent.
 */
object MarkdownFenceTracker {

    data class OpenFence(val char: Char, val length: Int)

    /** Returns an opening fence when [line] starts a fenced code block, otherwise `null`. */
    fun parseOpening(line: String): OpenFence? {
        val content = dropCodeFenceIndent(line)
        if (content.startsWith('`')) {
            val runLength = content.takeWhile { it == '`' }.length
            if (runLength < MIN_FENCE_LENGTH) {
                return null
            }
            if ('`' in content.drop(runLength)) {
                return null
            }
            return OpenFence('`', runLength)
        }
        if (content.startsWith('~')) {
            val runLength = content.takeWhile { it == '~' }.length
            if (runLength < MIN_FENCE_LENGTH) {
                return null
            }
            return OpenFence('~', runLength)
        }
        return null
    }

    /** Returns whether [line] closes [open] using the same marker with length ≥ the opener. */
    fun isClosing(line: String, open: OpenFence): Boolean {
        val content = dropCodeFenceIndent(line)
        if (content.firstOrNull() != open.char) {
            return false
        }
        val runLength = content.takeWhile { it == open.char }.length
        if (runLength < open.length) {
            return false
        }
        return content.drop(runLength).all { it.isWhitespace() }
    }

    private fun dropCodeFenceIndent(line: String): String {
        var spaces = 0
        while (spaces < MAX_INDENT && spaces < line.length && line[spaces] == ' ') {
            spaces += 1
        }
        return line.substring(spaces)
    }

    private const val MIN_FENCE_LENGTH = 3
    private const val MAX_INDENT = 3
}
