package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId

/** Read-only configuration from a Studio .flow marker. */
data class NoteflowConfig(
    val id: NoteId,
    val title: String,
    val links: List<String>,
    val settings: TodayHubFrontmatter.Settings
)
