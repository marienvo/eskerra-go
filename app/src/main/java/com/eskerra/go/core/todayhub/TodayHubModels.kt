package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry

/** One discovered Today hub: its note id and the tab label (parent folder name). */
data class TodayHubRef(val noteId: NoteId, val folderLabel: String)

/**
 * Loaded Today hub context (spec §11.1–§11.2): the discovered hubs, the active hub's settings and
 * intro body, the row stems present on disk, and the registry used for cell link resolution.
 */
data class TodayHubData(
    val hubs: List<TodayHubRef>,
    val activeHubId: NoteId,
    val settings: TodayHubFrontmatter.Settings,
    val introMarkdown: String,
    val availableWeekStems: List<String>,
    val registry: NoteRegistry
) {
    val columnCount: Int get() = TodayHubFrontmatter.columnCount(settings)
    val folderLabel: String get() = hubs.firstOrNull {
        it.noteId == activeHubId
    }?.folderLabel.orEmpty()
}

/**
 * One week's row, split into [columns] markdown sections (spec §11.3). [rowNoteId] is the synthetic
 * row uri used as the relative-link / image base directory when rendering each cell (§11.5).
 */
data class TodayHubRow(val rowNoteId: NoteId, val weekStartStem: String, val columns: List<String>)
