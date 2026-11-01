package com.eskerra.go.core.notifications

sealed interface DesktopActivityStatus {
    data object Inactive : DesktopActivityStatus
    data object Unavailable : DesktopActivityStatus
}

data class NotificationAlarmDecision(
    val skippedEventKeys: Set<String> = emptySet(),
    val nextAlarmAtEpochMillis: Long? = null
)

object DesktopActivityGate {
    const val INACTIVITY_THRESHOLD_MILLIS = 5 * 60 * 1000L
    const val RETRY_DELAY_MILLIS = 5 * 60 * 1000L

    fun isActive(lastActivityAtEpochMillis: Long, now: Long): Boolean =
        now - lastActivityAtEpochMillis < INACTIVITY_THRESHOLD_MILLIS

    fun decide(
        pending: List<NotificationEvent>,
        desktopActivity: DesktopActivityStatus,
        lastActivityAtEpochMillis: Long?,
        now: Long
    ): NotificationAlarmDecision {
        val due = pending.filter { it.dueAtEpochMillis <= now }
        val future = pending.filter { it.dueAtEpochMillis > now }
        if (due.isNotEmpty() && desktopActivity == DesktopActivityStatus.Unavailable) {
            return NotificationAlarmDecision(nextAlarmAtEpochMillis = now + RETRY_DELAY_MILLIS)
        }
        if (due.isNotEmpty() &&
            lastActivityAtEpochMillis?.let { isActive(it, now) } == true
        ) {
            return NotificationAlarmDecision(
                skippedEventKeys = due.mapTo(mutableSetOf()) { it.key },
                nextAlarmAtEpochMillis = future.minOfOrNull { it.dueAtEpochMillis }
            )
        }
        if (due.isNotEmpty()) return NotificationAlarmDecision()
        return NotificationAlarmDecision(
            nextAlarmAtEpochMillis = future.minOfOrNull { it.dueAtEpochMillis }
        )
    }
}
