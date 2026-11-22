package com.eskerra.go.core.notifications

object NotificationAlarmTiming {
    fun next(
        pending: List<NotificationEvent>,
        daily: List<DatedReminderSchedule>,
        now: Long
    ): Long? {
        val due = pending.any { it.dueAtEpochMillis <= now } ||
            daily.any {
                it.dueAtEpochMillis <= now
            }
        if (due) return now + DesktopActivityGate.RETRY_DELAY_MILLIS
        return (pending.map { it.dueAtEpochMillis } + daily.map { it.dueAtEpochMillis })
            .minOrNull()
    }
}
