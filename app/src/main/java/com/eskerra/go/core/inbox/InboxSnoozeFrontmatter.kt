package com.eskerra.go.core.inbox

import java.time.Instant

/** Reads the scalar format written by Eskerra without changing user-authored markdown. */
object InboxSnoozeFrontmatter {
    private val key = Regex("^snoozed_until\\s*:(.*)$")
    private val scalar = Regex("^\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s#]+))\\s*(?:#.*)?$")

    fun read(markdown: String): Instant? {
        val lines = markdown.removePrefix("\uFEFF").lineSequence().iterator()
        if (!lines.hasNext() || lines.next().trimEnd('\r') != "---") return null
        var value: String? = null
        var found = false
        while (lines.hasNext()) {
            val line = lines.next().trimEnd('\r')
            if (line == "---") return if (found) InboxSnooze.parse(value) else null
            val match = key.matchEntire(line) ?: continue
            if (found) return null
            found = true
            val parsed = scalar.matchEntire(match.groupValues[1]) ?: return null
            value = parsed.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        }
        return null
    }
}
