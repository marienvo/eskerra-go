package com.eskerra.go.core.datetime

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Mirrors `packages/eskerra-core/src/datetime/relativeCalendarLabel.ts`. */
object RelativeCalendarLabel {

    const val EM_DASH = "\u2014"

    private const val MS_PER_DAY = 86_400_000L
    private val ISO_DATE_ONLY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun startOfLocalDayMs(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        val localDate = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        return localDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }

    fun calendarDaysFromTargetToReference(
        targetMs: Long,
        referenceMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Int {
        val targetStart = startOfLocalDayMs(targetMs, timeZone)
        val referenceStart = startOfLocalDayMs(referenceMs, timeZone)
        return ((referenceStart - targetStart) / MS_PER_DAY).toInt()
    }

    fun format(
        targetMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (targetMs == null) {
            return EM_DASH
        }
        val diff = calendarDaysFromTargetToReference(targetMs, nowMs, timeZone)
        return when {
            diff < 0 -> formatIsoDateLocal(targetMs, timeZone)
            diff == 0 -> "Today"
            diff == 1 -> "Yesterday"
            diff in 2..6 -> weekdayLongLocal(targetMs, timeZone)
            else -> formatIsoDateLocal(targetMs, timeZone)
        }
    }

    fun formatFromIsoDate(
        isoDate: String,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val match = ISO_DATE_ONLY.matchEntire(isoDate.trim()) ?: return isoDate
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val targetMs = startOfLocalDayMs(
            epochMillis = java.time.LocalDate.of(year, month, day)
                .atStartOfDay(java.time.ZoneId.of(timeZone.id))
                .toInstant()
                .toEpochMilli(),
            timeZone = timeZone
        )
        val diff = calendarDaysFromTargetToReference(targetMs, nowMs, timeZone)
        return when {
            diff < 0 -> isoDate
            diff == 0 -> "Today"
            diff == 1 -> "Yesterday"
            diff in 2..6 -> weekdayLongLocal(targetMs, timeZone)
            else -> isoDate
        }
    }

    private fun formatIsoDateLocal(epochMillis: Long, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        val month = date.monthNumber.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return "${date.year}-$month-$day"
    }

    private fun weekdayLongLocal(epochMillis: Long, timeZone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
        val javaDay = java.time.DayOfWeek.of(date.dayOfWeek.value)
        return javaDay.getDisplayName(TextStyle.FULL, Locale.US)
    }
}
