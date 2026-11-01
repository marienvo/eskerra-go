package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteRegistry

/** Builds stable inbox and snooze occurrence identities from a registry snapshot. */
object InboxNotificationCandidates {
    fun fromRegistry(registry: NoteRegistry): List<NotificationEvent> =
        registry.inboxSummaries.map { note ->
            val snoozedUntil = note.snoozedUntilEpochMillis
            if (snoozedUntil > 0L) {
                NotificationEvent(
                    key = "snooze:${note.id.value}:$snoozedUntil",
                    noteId = note.id.value,
                    type = NotificationEventType.SNOOZE,
                    dueAtEpochMillis = snoozedUntil
                )
            } else {
                NotificationEvent(
                    key = "inbox:${note.id.value}",
                    noteId = note.id.value,
                    type = NotificationEventType.INBOX,
                    dueAtEpochMillis = 0L
                )
            }
        }
}
