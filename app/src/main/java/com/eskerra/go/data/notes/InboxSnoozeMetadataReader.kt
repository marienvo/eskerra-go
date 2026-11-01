package com.eskerra.go.data.notes

import com.eskerra.go.core.inbox.InboxSnoozeFrontmatter
import java.io.File

/** Reads only the leading frontmatter, never the remainder of the note. */
internal object InboxSnoozeMetadataReader {
    fun read(file: File): Long = file.bufferedReader(Charsets.UTF_8).use { reader ->
        val prefix = StringBuilder()
        var opened = false
        var line = reader.readLine()
        while (line != null) {
            val normalized = if (prefix.isEmpty()) line.removePrefix("\uFEFF") else line
            prefix.append(line).append('\n')
            if (!opened) {
                if (normalized.isBlank()) {
                    line = reader.readLine()
                    continue
                }
                if (normalized.trim() != "---") return@use 0L
                opened = true
            } else if (line.trimEnd() == "---") {
                return@use InboxSnoozeFrontmatter.read(prefix.toString())?.toEpochMilli() ?: 0L
            }
            line = reader.readLine()
        }
        0L
    }
}
