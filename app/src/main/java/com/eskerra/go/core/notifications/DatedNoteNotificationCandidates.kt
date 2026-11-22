package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import kotlinx.datetime.LocalDate

/** Registers every dated-folder note and announces available incomplete notes. */
object DatedNoteNotificationCandidates {
    fun select(
        registry: NoteRegistry,
        markers: List<NoteId>,
        today: LocalDate,
        content: Map<NoteId, String>
    ): List<NotificationEvent> = markers.flatMap { marker ->
        NoteflowDatedNotes.all(registry, marker).mapNotNull { target ->
            val dateText = target.noteId.value.substringAfterLast('/').removeSuffix(".md")
            val date =
                runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@mapNotNull null
            val markdown = content[target.noteId] ?: return@mapNotNull null
            val available = date <= today
            val incomplete = NoteflowDatedNotes.isIncomplete(markdown)
            NotificationEvent(
                key = "dated:${target.noteId.value}",
                noteId = target.noteId.value,
                type = if (available) {
                    NotificationEventType.DATED_NOTE
                } else {
                    NotificationEventType.FUTURE_DATED_NOTE
                },
                dueAtEpochMillis = if (available && incomplete) 0L else Long.MAX_VALUE,
                message = DatedNoteNotificationText.resolve(target.noteId.value, markdown)
            )
        }
    }.distinctBy { it.key }
}
