package com.eskerra.go.core.notifications

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object DatedNoteNotificationText {
    private val opening = Regex("\\A\\uFEFF?(?:[ \\t]*\\r?\\n)*---[ \\t]*(?:\\r?\\n|$)")
    private val closing = Regex("(?m)^---[ \\t]*\\r?$")

    fun resolve(noteId: String, markdown: String): String {
        val fallback = noteId.substringAfterLast('/').removeSuffix(".md")
        return runCatching {
            val start = opening.find(markdown)?.range?.last?.plus(1) ?: return@runCatching null
            val end = closing.find(markdown, start)?.range?.first ?: return@runCatching null
            val options = LoaderOptions().apply {
                isAllowDuplicateKeys = false
                maxAliasesForCollections = 20
            }
            val frontmatter = Yaml(SafeConstructor(options)).load<Any?>(
                markdown.substring(start, end)
            ) as? Map<*, *> ?: return@runCatching null
            (frontmatter["notification"] as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }
}
