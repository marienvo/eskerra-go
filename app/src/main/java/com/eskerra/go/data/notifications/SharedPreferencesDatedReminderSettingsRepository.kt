package com.eskerra.go.data.notifications

import android.content.Context
import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.repository.DatedReminderSettingsRepository

class SharedPreferencesDatedReminderSettingsRepository(context: Context) :
    DatedReminderSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        "dated_reminder_settings",
        Context.MODE_PRIVATE
    )

    override fun load(): DatedReminderSettings = DatedReminderSettings(
        enabled = preferences.getBoolean(ENABLED, true),
        minuteOfDay = preferences.getInt(MINUTE_OF_DAY, 7 * 60)
    )

    override fun save(settings: DatedReminderSettings): Result<Unit> = runCatching {
        check(
            preferences.edit()
                .putBoolean(ENABLED, settings.enabled)
                .putInt(MINUTE_OF_DAY, settings.minuteOfDay)
                .commit()
        )
    }

    private companion object {
        const val ENABLED = "enabled"
        const val MINUTE_OF_DAY = "minute_of_day"
    }
}
