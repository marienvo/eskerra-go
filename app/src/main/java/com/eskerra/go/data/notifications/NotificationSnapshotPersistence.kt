package com.eskerra.go.data.notifications

import com.eskerra.go.core.notifications.NotificationEvent

interface NotificationSnapshotPersistence {
    fun cachedReminders(
        workspace: String,
        noteId: String,
        signature: String
    ): List<NotificationEvent>?
    fun cacheReminders(
        workspace: String,
        noteId: String,
        signature: String,
        events: List<NotificationEvent>
    )
    fun replaceSnapshot(workspace: String, events: List<NotificationEvent>, now: Long)
}
