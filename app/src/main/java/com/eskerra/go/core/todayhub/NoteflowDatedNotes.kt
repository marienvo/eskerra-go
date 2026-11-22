package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.vault.VaultVisibility
import kotlinx.datetime.LocalDate

/** Selects the same dated-folder targets as the Studio Noteflow header. */
object NoteflowDatedNotes {
    data class Target(val folder: String, val noteId: NoteId)
    data class Neighbours(val previous: NoteId?, val next: NoteId?, val date: LocalDate)

    fun neighbours(registry: NoteRegistry, marker: NoteId, current: NoteId): Neighbours? {
        val folderPath = TodayHubDiscovery.directoryOf(current.value)
        val folder = folderPath.substringAfterLast('/')
        if (TodayHubDiscovery.directoryOf(
                folderPath
            ) != TodayHubDiscovery.directoryOf(marker.value) ||
            folder == "Inbox" ||
            VaultVisibility.isExcludedDirectorySegment(folder)
        ) {
            return null
        }
        val name = current.value.substringAfterLast('/')
        if (!name.endsWith(".md")) return null
        val date = TodayHubWeeks.parseRowStem(name.removeSuffix(".md")) ?: return null
        val siblings = registry.notes.mapNotNull { note ->
            if (TodayHubDiscovery.directoryOf(note.id.value) != folderPath) return@mapNotNull null
            val file = note.id.value.substringAfterLast('/')
            if (!file.endsWith(".md")) return@mapNotNull null
            val siblingDate = TodayHubWeeks.parseRowStem(file.removeSuffix(".md"))
                ?: return@mapNotNull null
            siblingDate to note.id
        }.sortedBy { it.first }
        if (siblings.none { it.second == current }) return null
        return Neighbours(
            previous = siblings.lastOrNull { it.first < date }?.second,
            next = siblings.firstOrNull { it.first > date }?.second,
            date = date
        )
    }

    fun select(registry: NoteRegistry, marker: NoteId, today: LocalDate): List<Target> =
        all(registry, marker).groupBy { it.folder }.toSortedMap().map { (folder, notes) ->
            val selected = notes.lastOrNull {
                TodayHubWeeks.parseRowStem(
                    it.noteId.value.substringAfterLast('/').removeSuffix(".md")
                )!! <= today
            } ?: notes.first()
            Target(folder, selected.noteId)
        }

    fun all(registry: NoteRegistry, marker: NoteId): List<Target> {
        val hubDir = TodayHubDiscovery.directoryOf(marker.value)
        return registry.notes.mapNotNull { note ->
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
        }.sortedWith(compareBy({ it.first }, { it.second })).map {
            Target(it.first, it.third)
        }
    }

    fun isIncomplete(markdown: String): Boolean = !DatedNoteReadStatus.isRead(markdown)
}
