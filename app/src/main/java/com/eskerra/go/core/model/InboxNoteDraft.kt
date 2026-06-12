package com.eskerra.go.core.model

import com.eskerra.go.core.inbox.InboxMarkdownFileName

/**
 * Converts compose-form draft text into inbox note markdown and a safe filename stem.
 * Mirrors `@eskerra/core` `inboxComposeNote.ts` and `inboxMarkdown.ts`.
 */
object InboxNoteDraft {

    const val UNTITLED_STEM = "untitled"
    private const val H1_PREFIX = "# "

    data class ParsedComposeInput(val titleLine: String, val bodyAfterBlank: String)

    fun parseComposeInput(raw: String): ParsedComposeInput {
        val lines = raw.split("\r\n", "\n")
        val titleLine = lines.firstOrNull()?.trim().orEmpty()
        val bodyAfterBlank = lines.drop(1).joinToString("\n").trim()
        return ParsedComposeInput(titleLine = titleLine, bodyAfterBlank = bodyAfterBlank)
    }

    fun extractTitleLine(draft: String): String = parseComposeInput(draft).titleLine.let { title ->
        if (title.startsWith(H1_PREFIX)) title.removePrefix(H1_PREFIX).trim() else title
    }

    fun hasNonBlankTitle(draft: String): Boolean = extractTitleLine(draft).isNotBlank()

    fun toMarkdown(draft: String): String {
        val parsed = parseComposeInput(draft)
        val normalizedTitle = parsed.titleLine.removePrefix(H1_PREFIX).trim()
        val normalizedBody = parsed.bodyAfterBlank.trim()
        return if (normalizedBody.isEmpty()) {
            "$H1_PREFIX$normalizedTitle\n"
        } else {
            "$H1_PREFIX$normalizedTitle\n\n$normalizedBody"
        }
    }

    fun fromMarkdownToComposeInput(markdown: String): String {
        val trimmed = markdown.trimEnd()
        if (trimmed.isEmpty()) {
            return ""
        }

        val lines = trimmed.split("\r\n", "\n")
        val firstLine = lines.firstOrNull().orEmpty()
        val h1Match = Regex("^#\\s+(.*)$").matchEntire(firstLine)

        if (h1Match != null) {
            val titleLine = h1Match.groupValues[1].trim()
            val restLines = lines.drop(1)
            val startIndex = restLines.indexOfFirst { it.trim().isNotEmpty() }.let { index ->
                if (index < 0) restLines.size else index
            }
            val body = restLines.drop(startIndex).joinToString("\n").trimEnd()
            return if (body.isEmpty()) titleLine else "$titleLine\n\n$body"
        }

        val titleLine = firstLine.trim()
        val body = lines.drop(1).joinToString("\n").trim()
        return if (body.isEmpty()) titleLine else "$titleLine\n\n$body"
    }

    fun toFilenameStem(title: String, nowEpochMillis: Long = System.currentTimeMillis()): String =
        InboxMarkdownFileName.sanitizeFileName(title, nowEpochMillis)
}
