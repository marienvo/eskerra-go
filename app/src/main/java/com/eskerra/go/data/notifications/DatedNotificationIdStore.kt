package com.eskerra.go.data.notifications

import android.content.Context

class DatedNotificationIdStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "dated_notification_ids",
        Context.MODE_PRIVATE
    )

    fun idFor(noteId: String): Int = synchronized(LOCK) {
        preferences.getInt(noteId, MISSING).takeIf { it != MISSING }?.let {
            return@synchronized it
        }
        val occupied = preferences.all.values.filterIsInstance<Int>().toSet()
        var candidate = noteId.hashCode() and Int.MAX_VALUE
        while (candidate == 0 || candidate in RESERVED_IDS || candidate in occupied) {
            candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        }
        check(preferences.edit().putInt(noteId, candidate).commit())
        candidate
    }

    private companion object {
        const val MISSING = -1
        val RESERVED_IDS = setOf(1001, 4200)
        val LOCK = Any()
    }
}
