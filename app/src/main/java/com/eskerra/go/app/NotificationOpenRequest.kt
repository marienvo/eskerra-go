package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId

data class NotificationOpenRequest(val noteIds: List<String>, val requestId: Long = 0) {
    internal fun destination(launchSettled: Boolean, handledRequestId: Long?): String? {
        if (!launchSettled || requestId == handledRequestId) return null
        return when (noteIds.size) {
            0 -> null
            1 -> AppRoute.notificationNote(NoteId(noteIds.single()))
            else -> AppRoute.NOTIFICATION_LIST
        }
    }

    companion object {
        fun next(noteIds: List<String>, previous: NotificationOpenRequest) =
            NotificationOpenRequest(noteIds, previous.requestId + 1)
    }
}
