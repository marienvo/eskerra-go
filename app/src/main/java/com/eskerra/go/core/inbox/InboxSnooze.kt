package com.eskerra.go.core.inbox

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Shared snooze time rules: 09:00 in the zone where the user chose the date. */
object InboxSnooze {
    fun until(date: LocalDate, zone: ZoneId): Instant =
        date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()

    fun isHidden(until: Instant?, now: Instant): Boolean = until != null && until.isAfter(now)
}
