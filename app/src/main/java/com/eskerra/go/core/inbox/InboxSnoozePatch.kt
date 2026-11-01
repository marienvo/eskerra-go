package com.eskerra.go.core.inbox

import java.time.Instant

/** Modify only the snooze property while preserving every other markdown byte. */
object InboxSnoozePatch {
    fun apply(markdown: String, until: Instant): Result<String> = runCatching {
        val block = InboxYamlFrontmatter.parse(markdown)
        val newline = if (markdown.contains("\r\n")) "\r\n" else "\n"
        val field = "snoozed_until: \"$until\""
        if (block == null) {
            require(!markdown.removePrefix("\uFEFF").trimStart().startsWith("---")) {
                "Invalid frontmatter fence"
            }
            val bom = if (markdown.startsWith('\uFEFF')) "\uFEFF" else ""
            return@runCatching bom + "---$newline$field$newline---$newline" +
                markdown.removePrefix(bom)
        }
        val node = InboxYamlFrontmatter.valueNode(block)
        if (node == null) {
            return@runCatching markdown.substring(0, block.end) + field + newline +
                markdown.substring(block.end)
        }
        val start = block.inner.offsetByCodePoints(0, node.startMark.index)
        val end = block.inner.offsetByCodePoints(0, node.endMark.index)
        require(start < end && !block.inner.substring(start, end).contains('\n')) {
            "Unsupported snooze value"
        }
        markdown.substring(0, block.start + start) + "\"$until\"" +
            markdown.substring(block.start + end)
    }
}
