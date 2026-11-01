package com.eskerra.go.core.inbox

import java.time.Instant

/** Narrow, byte-preserving patch. Unsupported YAML is rejected rather than rewritten. */
object InboxSnoozePatch {
    private val scalarLine = Regex("^[A-Za-z_][A-Za-z0-9_-]*:[ \\t]*[^\\[\\]{}&*!|>]*$")
    private val snoozeLine = Regex("^snoozed_until[ \\t]*:.*$")

    fun apply(markdown: String, until: Instant): Result<String> = runCatching {
        val bom = if (markdown.startsWith('\uFEFF')) "\uFEFF" else ""
        val content = markdown.removePrefix(bom)
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"
        val field = "snoozed_until: \"$until\""
        if (!content.startsWith("---$newline")) {
            require(!content.trimStart().startsWith("---")) { "Unsupported frontmatter fence" }
            return@runCatching "$bom---$newline$field$newline---$newline$content"
        }
        val start = 3 + newline.length
        val closing = content.indexOf("$newline---", start - newline.length)
        require(closing >= 0) { "Unclosed frontmatter" }
        val fenceEnd = closing + newline.length + 3
        require(fenceEnd == content.length || content.startsWith(newline, fenceEnd)) {
            "Invalid frontmatter fence"
        }
        val inner = content.substring(start, closing.coerceAtLeast(start))
        val lines = if (inner.isEmpty()) emptyList() else inner.split(newline)
        val keys = mutableSetOf<String>()
        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith('#')) continue
            require(scalarLine.matches(line)) { "Unsupported frontmatter" }
            val value = line.substringAfter(':').trim()
            require(value.count { it == '\"' } % 2 == 0 && value.count { it == '\'' } % 2 == 0) {
                "Unsupported quoted scalar"
            }
            require(keys.add(line.substringBefore(':'))) { "Duplicate frontmatter key" }
        }
        val index = lines.indexOfFirst { snoozeLine.matches(it) }
        val updated = if (index < 0) {
            lines + field
        } else {
            lines.toMutableList().apply { this[index] = field }
        }
        val prefix = content.substring(0, start)
        val suffix = content.substring(fenceEnd)
        bom + prefix + updated.joinToString(newline) + newline + "---" + suffix
    }
}
