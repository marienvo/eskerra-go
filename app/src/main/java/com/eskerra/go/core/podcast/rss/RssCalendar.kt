package com.eskerra.go.core.podcast.rss

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local-calendar day math shared by the RSS composer and stub merge. Mirrors
 * `calendarDaysFromTargetToReference`: the count of whole calendar days from a
 * target day forward to a reference day (0 = same day, 1 = target is yesterday).
 */
object RssCalendar {

    fun isoDate(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toString()

    fun daysFromTargetToReference(targetIso: String, referenceMillis: Long, zoneId: ZoneId): Int? {
        val targetDay = runCatching { LocalDate.parse(targetIso) }.getOrNull() ?: return null
        val referenceDay = Instant.ofEpochMilli(referenceMillis).atZone(zoneId).toLocalDate()
        return (referenceDay.toEpochDay() - targetDay.toEpochDay()).toInt()
    }
}
