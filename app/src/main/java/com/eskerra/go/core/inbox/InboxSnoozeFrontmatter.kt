package com.eskerra.go.core.inbox

import java.time.Instant

/** Invalid or ambiguous frontmatter never hides a note. */
object InboxSnoozeFrontmatter {
    fun read(markdown: String): Instant? = runCatching {
        val block = InboxYamlFrontmatter.parse(markdown) ?: return null
        val node = InboxYamlFrontmatter.valueNode(block) ?: return null
        InboxSnooze.parse(node.value)
    }.getOrNull()
}
