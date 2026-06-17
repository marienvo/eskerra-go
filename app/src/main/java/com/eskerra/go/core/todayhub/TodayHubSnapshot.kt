package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId

/**
 * Minimal renderable Today Hub snapshot. The note registry is intentionally not duplicated here;
 * callers rehydrate it from the registry cache before turning this into UI state.
 */
data class TodayHubSnapshot(
    val hubs: List<TodayHubRef>,
    val activeHubId: NoteId,
    val settings: TodayHubFrontmatter.Settings,
    val introMarkdown: String,
    val availableWeekStems: List<String>,
    val selectedWeekStem: String,
    val row: TodayHubRow?
)
