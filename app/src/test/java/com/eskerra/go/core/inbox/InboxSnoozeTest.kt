package com.eskerra.go.core.inbox

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxSnoozeTest {
    @Test
    fun usesLocalNineAcrossDaylightSavingChange() {
        val zone = ZoneId.of("Europe/Amsterdam")
        assertEquals(
            Instant.parse("2026-03-28T08:00:00Z"),
            InboxSnooze.until(LocalDate.parse("2026-03-28"), zone)
        )
        assertEquals(
            Instant.parse("2026-03-29T07:00:00Z"),
            InboxSnooze.until(LocalDate.parse("2026-03-29"), zone)
        )
    }

    @Test
    fun tomorrowUsesCalendarDateInsteadOfTwentyFourHours() {
        val clock = Clock.fixed(
            Instant.parse("2026-03-28T22:30:00Z"),
            ZoneId.of("Europe/Amsterdam")
        )
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), InboxSnooze.tomorrow(clock))
    }

    @Test
    fun acceptsOffsetTimestampsAndRejectsInvalidValues() {
        assertEquals(
            Instant.parse("2026-11-02T08:00:00Z"),
            InboxSnooze.parse("2026-11-02T09:00:00+01:00")
        )
        assertNull(InboxSnooze.parse("2026-11-02"))
        assertNull(InboxSnooze.parse("2026-11-02T09:00Z"))
        assertNull(InboxSnooze.parse("2026-11-02T09:00:00+19:00"))
        assertNull(InboxSnooze.parse("2026-11-02T09:00:00.1234567890Z"))
        assertNull(InboxSnooze.parse(null))
    }

    @Test
    fun hidesOnlyBeforeDeadline() {
        val deadline = Instant.parse("2026-11-02T08:00:00Z")
        assertTrue(InboxSnooze.isHidden(deadline, deadline.minusSeconds(1)))
        assertFalse(InboxSnooze.isHidden(deadline, deadline))
        assertFalse(InboxSnooze.isHidden(null, deadline))
    }
}
