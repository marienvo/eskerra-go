package com.eskerra.go.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopActivityGateTest {
    private val now = 1_000_000L
    private val due = NotificationEvent("due", "a.md", NotificationEventType.INBOX, now)
    private val future = NotificationEvent(
        "future",
        "b.md",
        NotificationEventType.REMINDER,
        now + 50_000
    )

    @Test
    fun activeDesktopSkipsOnlyDueEvents() {
        val decision = DesktopActivityGate.decide(
            listOf(due, future),
            DesktopActivityStatus.Inactive,
            now - DesktopActivityGate.INACTIVITY_THRESHOLD_MILLIS + 1,
            now
        )
        assertEquals(setOf("due"), decision.skippedEventKeys)
        assertEquals(future.dueAtEpochMillis, decision.nextAlarmAtEpochMillis)
    }

    @Test
    fun fiveMinutesMeansInactive() {
        assertFalse(
            DesktopActivityGate.isActive(now - DesktopActivityGate.INACTIVITY_THRESHOLD_MILLIS, now)
        )
        assertTrue(DesktopActivityGate.isActive(now - 1, now))
    }

    @Test
    fun unavailableR2KeepsDueEventsAndRetries() {
        val decision = DesktopActivityGate.decide(
            listOf(due, future),
            DesktopActivityStatus.Unavailable,
            null,
            now
        )
        assertTrue(decision.skippedEventKeys.isEmpty())
        assertEquals(now + DesktopActivityGate.RETRY_DELAY_MILLIS, decision.nextAlarmAtEpochMillis)
    }

    @Test
    fun inactiveDesktopWaitsForDeliveryWithoutAnotherAlarm() {
        val decision = DesktopActivityGate.decide(
            listOf(due),
            DesktopActivityStatus.Inactive,
            now - DesktopActivityGate.INACTIVITY_THRESHOLD_MILLIS,
            now
        )
        assertTrue(decision.skippedEventKeys.isEmpty())
        assertNull(decision.nextAlarmAtEpochMillis)
    }

    @Test
    fun futureEventsDoNotRequireR2() {
        val decision = DesktopActivityGate.decide(
            listOf(future),
            DesktopActivityStatus.Inactive,
            null,
            now
        )
        assertEquals(future.dueAtEpochMillis, decision.nextAlarmAtEpochMillis)
    }
}
