package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.vault.VaultVisibility
import kotlinx.datetime.LocalDate

/** Selects the same dated-folder targets as the Studio Noteflow header. */
object NoteflowDatedNotes {
    data class Target(val folder: String, val noteId: NoteId)

    fun select(registry: NoteRegistry, marker: NoteId, today: LocalDate): List<Target> {
        val hubDir = TodayHubDiscovery.directoryOf(marker.value)
        val candidates = registry.notes.mapNotNull { note ->
            val folderPath = TodayHubDiscovery.directoryOf(note.id.value)
            if (TodayHubDiscovery.directoryOf(folderPath) != hubDir) return@mapNotNull null
            val folder = folderPath.substringAfterLast('/')
            if (folder == "Inbox" || VaultVisibility.isExcludedDirectorySegment(folder)) {
                return@mapNotNull null
            }
            val name = note.id.value.substringAfterLast('/')
            if (!name.endsWith(".md")) return@mapNotNull null
            val date = TodayHubWeeks.parseRowStem(name.removeSuffix(".md"))
                ?: return@mapNotNull null
            Triple(folder, date, note.id)
        }
        return candidates.groupBy { it.first }.toSortedMap().map { (folder, notes) ->
            val sorted = notes.sortedBy { it.second }
            val selected = sorted.lastOrNull { it.second <= today } ?: sorted.first()
            Target(folder, selected.third)
        }
    }

    fun isIncomplete(markdown: String): Boolean {
        val title = markdown.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
        return title == null || !title.contains("✅")
    }
}
