package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubWeeks

/** Stateless view model output for the Today Hub screen (spec §11). */
sealed interface TodayHubUiState {

    data object Loading : TodayHubUiState

    /** Index ready but no `Today.md` exists anywhere in the vault (spec §11.6). */
    data object Empty : TodayHubUiState

    data class Error(val message: String) : TodayHubUiState

    data class Content(
        val hubs: List<TodayHubRef>,
        val activeHubId: NoteId,
        val folderLabel: String,
        val introMarkdown: String,
        val registry: NoteRegistry,
        /** One header per column: index 0 is the long week-start date, the rest are configured names. */
        val columnHeaders: List<String>,
        val selectedWeekStem: String,
        val weekRangeLabel: String,
        val canGoPrev: Boolean,
        val canGoNext: Boolean,
        /** Week progress strip segments for the date column header. */
        val progressSegments: List<TodayHubWeeks.ProgressSegment>,
        /** The loaded week row, or `null` until the first row fetch completes. */
        val row: TodayHubRow?,
        /** True when a slow (>200 ms) row fetch is in flight (spec §11.4). */
        val rowLoading: Boolean
    ) : TodayHubUiState {
        val showHubPicker: Boolean get() = hubs.size > 1
    }
}
