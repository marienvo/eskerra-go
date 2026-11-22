package com.eskerra.go.core.notifications

data class DatedReminderSettings(val enabled: Boolean = true, val minuteOfDay: Int = 7 * 60) {
    init {
        require(minuteOfDay in 0 until 24 * 60)
    }

    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
}

data class DatedReminderSchedule(val folder: String, val dueAtEpochMillis: Long)

data class DatedReminderHandled(val noteId: String, val day: String)
