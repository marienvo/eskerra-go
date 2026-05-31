package com.eskerra.go.core.model

/** Converts compose-form draft text into inbox note markdown and a safe filename stem. */
object InboxNoteDraft {

    const val UNTITLED_STEM = "untitled"
    private const val H1_PREFIX = "# "

    fun extractTitleLine(draft: String): String {
        val firstLine = draft.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine.startsWith(H1_PREFIX)) {
            firstLine.removePrefix(H1_PREFIX).trim()
        } else {
            firstLine
        }
    }

    fun hasNonBlankTitle(draft: String): Boolean = extractTitleLine(draft).isNotBlank()

    fun toMarkdown(draft: String): String {
        if (draft.isEmpty()) {
            return "# \n\n"
        }

        val lines = draft.lines()
        val trimmedFirst = lines.first().trim()
        val h1Line = if (trimmedFirst.startsWith(H1_PREFIX)) {
            trimmedFirst
        } else {
            "$H1_PREFIX$trimmedFirst"
        }

        if (lines.size == 1) {
            return "$h1Line\n\n"
        }

        val tail = lines.drop(1).joinToString("\n")
        return if (lines.getOrNull(1).isNullOrEmpty()) {
            "$h1Line\n$tail"
        } else {
            "$h1Line\n\n$tail"
        }
    }

    fun toFilenameStem(title: String): String {
        val sanitized = title
            .map { char ->
                when {
                    char.isISOControl() -> ' '
                    char in INVALID_FILENAME_CHARS -> ' '
                    else -> char
                }
            }
            .joinToString("")
            .trim()
        return sanitized.ifEmpty { UNTITLED_STEM }
    }

    private val INVALID_FILENAME_CHARS = charArrayOf(
        '/', '\\', ':', '*', '?', '"', '<', '>', '|'
    )
}
