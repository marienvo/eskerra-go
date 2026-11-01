package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class DatedNoteHeader(
    val links: List<TodayHubDatedLink>,
    val current: NoteId,
    val previous: NoteId?,
    val next: NoteId?,
    val dateLabel: String
) {
    companion object {
        fun dateLabel(date: LocalDate, today: LocalDate): String = when (date.daysUntil(today)) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> date.toString()
        }
    }
}
