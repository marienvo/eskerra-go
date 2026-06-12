package com.eskerra.go.core.inbox

/** Mirrors `packages/eskerra-core/src/inboxMarkdown.ts` filename helpers. */
object InboxMarkdownFileName {

    const val MARKDOWN_EXTENSION = ".md"

    private val ILLEGAL_FILENAME_CHARS = setOf(
        '/', '\\', ':', '*', '?', '"', '<', '>', '|'
    )
    private val EDGE_TRIM_CHARS = setOf('.', ' ')

    fun sanitizeInboxNoteStem(rawName: String): String? {
        val withoutControlChars = rawName.trim()
            .filter { char -> char.code >= ' '.code && char.code != 0x7f }
        val normalized = withoutControlChars
            .let(::stripIllegalFilenameChars)
            .let(::collapseAsciiWhitespaceRunsToSpace)
            .let(::trimLeadingDotsAndSpaces)
            .let(::trimTrailingDotsAndSpaces)
        return normalized.ifEmpty { null }
    }

    fun sanitizeFileName(
        rawName: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String = sanitizeInboxNoteStem(rawName) ?: "note-$nowEpochMillis"

    fun pickNextInboxMarkdownFileName(
        baseStem: String,
        occupiedMarkdownNames: Set<String>
    ): String {
        var candidate = "$baseStem$MARKDOWN_EXTENSION"
        var nextSuffix = 2
        while (candidate in occupiedMarkdownNames) {
            candidate = "$baseStem-$nextSuffix$MARKDOWN_EXTENSION"
            nextSuffix += 1
        }
        return candidate
    }

    private fun stripIllegalFilenameChars(value: String): String =
        value.filterNot { it in ILLEGAL_FILENAME_CHARS }

    private fun collapseAsciiWhitespaceRunsToSpace(value: String): String {
        val builder = StringBuilder()
        var previousWasWhitespace = false
        for (char in value) {
            if (char.isWhitespace()) {
                if (!previousWasWhitespace) {
                    builder.append(' ')
                    previousWasWhitespace = true
                }
            } else {
                builder.append(char)
                previousWasWhitespace = false
            }
        }
        return builder.toString()
    }

    private fun trimLeadingDotsAndSpaces(value: String): String =
        value.dropWhile { it in EDGE_TRIM_CHARS }

    private fun trimTrailingDotsAndSpaces(value: String): String =
        value.dropLastWhile { it in EDGE_TRIM_CHARS }
}
