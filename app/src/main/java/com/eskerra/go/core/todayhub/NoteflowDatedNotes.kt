package com.eskerra.go.core.todayhub

import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
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
        val body = VaultMarkdownPreprocess.splitYamlFrontmatter(markdown).body
        val preview = body.lineSequence().take(60).joinToString("\n").take(4000)
        val title = preview.lineSequence().map { it.trim() }.firstOrNull {
            it.startsWith("#") && !it.startsWith("##") && it.drop(1).isNotBlank()
        }
        return title == null || !title.contains("✅")
    }
}
