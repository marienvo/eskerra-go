package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import kotlinx.datetime.LocalDate

/** Dated-folder targets are announced only from their date and while incomplete. */
object DatedNoteNotificationCandidates {
    fun select(
        registry: NoteRegistry,
        markers: List<NoteId>,
        today: LocalDate,
        content: Map<NoteId, String>
    ): List<NotificationEvent> = markers.flatMap { marker ->
        NoteflowDatedNotes.select(registry, marker, today).mapNotNull { target ->
            val dateText = target.noteId.value.substringAfterLast('/').removeSuffix(".md")
            val date =
                runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@mapNotNull null
            val markdown = content[target.noteId] ?: return@mapNotNull null
            if (date > today || !NoteflowDatedNotes.isIncomplete(markdown)) return@mapNotNull null
            NotificationEvent(
                key = "dated:${target.noteId.value}",
                noteId = target.noteId.value,
                type = NotificationEventType.DATED_NOTE,
                dueAtEpochMillis = 0L
            )
        }
    }.distinctBy { it.key }
}
