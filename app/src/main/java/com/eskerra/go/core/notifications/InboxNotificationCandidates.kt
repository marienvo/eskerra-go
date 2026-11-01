package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteRegistry

/** Retains inbox identity independently of an optional snooze occurrence. */
object InboxNotificationCandidates {
    fun fromRegistry(registry: NoteRegistry): List<NotificationEvent> =
        registry.inboxSummaries.flatMap { note ->
            val snoozedUntil = note.snoozedUntilEpochMillis
            val inbox = NotificationEvent(
                key = "inbox:${note.id.value}",
                noteId = note.id.value,
                type = NotificationEventType.INBOX,
                dueAtEpochMillis = if (snoozedUntil > 0L) Long.MAX_VALUE else 0L
            )
            if (snoozedUntil > 0L) {
                listOf(
                    inbox,
                    NotificationEvent(
                        key = "snooze:${note.id.value}:$snoozedUntil",
                        noteId = note.id.value,
                        type = NotificationEventType.SNOOZE,
                        dueAtEpochMillis = snoozedUntil
                    )
                )
            } else {
                listOf(inbox)
            }
        }
}
