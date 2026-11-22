package com.eskerra.go.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationAlarmTimingTest {
    private val now = 1_000_000L

    @Test
    fun undeliveredDueEventRetriesAfterTemporaryFailureOrMissingPermission() {
        val pending = NotificationEvent("a", "a.md", NotificationEventType.DATED_NOTE, now)
        assertEquals(
            now + DesktopActivityGate.RETRY_DELAY_MILLIS,
            NotificationAlarmTiming.next(listOf(pending), emptyList(), now)
        )
    }

    @Test
    fun earliestFutureAlarmWins() {
        val pending = NotificationEvent("a", "a.md", NotificationEventType.REMINDER, now + 20_000)
        val daily = DatedReminderSchedule("Flow/Work", now + 10_000)
        assertEquals(
            daily.dueAtEpochMillis,
            NotificationAlarmTiming.next(listOf(pending), listOf(daily), now)
        )
    }

    @Test
    fun emptyQueuesCancelTheAlarm() {
        assertNull(NotificationAlarmTiming.next(emptyList(), emptyList(), now))
    }
}
