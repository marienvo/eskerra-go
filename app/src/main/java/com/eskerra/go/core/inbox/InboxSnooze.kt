package com.eskerra.go.core.inbox

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Shared snooze time rules: 09:00 in the zone where the user chose the date. */
object InboxSnooze {
    private val timestamp = Regex(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}" +
            "(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$"
    )
    fun until(date: LocalDate, zone: ZoneId): Instant =
        date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()

    fun tomorrow(clock: Clock): Instant = until(LocalDate.now(clock).plusDays(1), clock.zone)

    /** Parse an offset timestamp; malformed metadata never hides a note. */
    fun parse(value: String?): Instant? = try {
        value?.takeIf { timestamp.matches(it) }?.let { OffsetDateTime.parse(it).toInstant() }
    } catch (_: DateTimeParseException) {
        null
    }

    fun isHidden(until: Instant?, now: Instant): Boolean = until != null && until.isAfter(now)
}
