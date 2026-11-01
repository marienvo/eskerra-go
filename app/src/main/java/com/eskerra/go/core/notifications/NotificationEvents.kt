package com.eskerra.go.core.notifications

/** Stable identity of a note occurrence, independent of its display text. */
data class NotificationEvent(
    val key: String,
    val noteId: String,
    val type: NotificationEventType,
    val dueAtEpochMillis: Long
)

enum class NotificationEventType { INBOX, SNOOZE, DATED_NOTE, REMINDER }

/** Computes candidates whose deadlines crossed since the last successful snapshot. */
object NotificationEvents {
    fun newlyDue(
        previous: Map<String, NotificationEvent>?,
        previousScanAt: Long,
        current: List<NotificationEvent>,
        now: Long
    ): List<NotificationEvent> {
        if (previous == null) return emptyList()
        return current.distinctBy { it.key }.filter { event ->
            val old = previous[event.key]
            event.dueAtEpochMillis <= now &&
                if (old != null) {
                    old.dueAtEpochMillis != Long.MAX_VALUE && old.dueAtEpochMillis > previousScanAt
                } else {
                    event.type != NotificationEventType.REMINDER
                }
        }
    }
}
