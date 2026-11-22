package com.eskerra.go.core.repository

import com.eskerra.go.core.notifications.DatedReminderSettings

interface DatedReminderSettingsRepository {
    fun load(): DatedReminderSettings
    fun save(settings: DatedReminderSettings): Result<Unit>
}
