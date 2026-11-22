package com.eskerra.go.data.notifications

interface NotificationAlarmScheduling {
    fun schedule(atEpochMillis: Long)
    fun cancel()
}
