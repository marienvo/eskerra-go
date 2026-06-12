package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry

/**
 * Today hub discovery over the vault note registry (spec §11.1). Mirrors the path logic of
 * `packages/eskerra-core/src/todayHub/vaultTodayHub.ts`, simplified for this app's plain
 * vault-relative [NoteId] paths (no Storage Access Framework document ids — see plan §0).
 */
object TodayHubDiscovery {

    /** An eligible markdown file with this exact name makes its directory a Today hub. */
    const val TODAY_HUB_NOTE_NAME = "Today.md"

    private val ROW_FILE_RE = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")

    private fun fileName(path: String): String = path.substringAfterLast('/')

    /** Directory containing [path], or `""` when [path] sits at the vault root. */
    fun directoryOf(path: String): String =
        if (path.contains('/')) path.substringBeforeLast('/') else ""

    fun isTodayHubNote(path: String): Boolean = fileName(path) == TODAY_HUB_NOTE_NAME

    /** Tab label for a hub: the parent folder name, falling back to the file stem at vault root. */
    fun folderLabel(todayNotePath: String): String {
        val dir = directoryOf(todayNotePath)
        val segment = dir.substringAfterLast('/').ifEmpty { dir }
        if (segment.isNotEmpty()) return segment
        return fileName(todayNotePath).removeSuffix(".md")
    }

    /** All `Today.md` note ids in the registry, sorted for a stable "first hub". */
    fun sortedHubNoteIds(registry: NoteRegistry): List<NoteId> = registry.notes
        .filter { isTodayHubNote(it.id.value) }
        .map { it.id }
        .sortedBy { it.value }

    /** Row note id for `YYYY-MM-DD.md` beside the hub note. */
    fun rowNoteId(todayNotePath: String, weekStartStem: String): NoteId {
        val dir = directoryOf(todayNotePath)
        val tail = "$weekStartStem.md"
        return NoteId(if (dir.isEmpty()) tail else "$dir/$tail")
    }

    /** Sorted (ascending) `YYYY-MM-DD` stems of row files beside the hub note in the registry. */
    fun availableWeekStems(todayNotePath: String, registry: NoteRegistry): List<String> {
        val hubDir = directoryOf(todayNotePath)
        val stems = sortedSetOf<String>()
        for (note in registry.notes) {
            if (directoryOf(note.id.value) != hubDir) continue
            val name = fileName(note.id.value)
            if (!ROW_FILE_RE.matches(name)) continue
            val stem = name.removeSuffix(".md")
            if (TodayHubWeeks.parseRowStem(stem) != null) {
                stems += stem
            }
        }
        return stems.toList()
    }
}
