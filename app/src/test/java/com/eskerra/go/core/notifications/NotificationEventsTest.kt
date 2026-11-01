package com.eskerra.go.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEventsTest {
    private val reminder =
        NotificationEvent("reminder:a", "Inbox/a.md", NotificationEventType.REMINDER, 15)

    @Test
    fun futureRemindersAreQueuedForAlarmScheduling() {
        assertEquals(
            listOf(reminder),
            NotificationEvents.upcoming(listOf(reminder), 10)
        )
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.upcoming(listOf(reminder), 15)
        )
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.upcoming(
                listOf(reminder.copy(dueAtEpochMillis = Long.MAX_VALUE)),
                10
            )
        )
    }

    @Test
    fun firstScanDoesNotEmitHistoricalEvents() {
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.newlyDue(null, 0, listOf(reminder), 20)
        )
    }

    @Test
    fun knownFutureReminderFiresWhenItsDeadlineCrosses() {
        assertEquals(
            listOf(reminder),
            NotificationEvents.newlyDue(mapOf(reminder.key to reminder), 10, listOf(reminder), 20)
        )
    }

    @Test
    fun alreadyDueAndNewHistoricalRemindersAreNotEmitted() {
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.newlyDue(
                mapOf(
                    reminder.key to reminder
                ),
                20,
                listOf(reminder),
                30
            )
        )
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.newlyDue(emptyMap(), 20, listOf(reminder), 30)
        )
    }

    @Test
    fun newlyDiscoveredOverdueReminderIsNotReported() {
        assertEquals(
            emptyList<NotificationEvent>(),
            NotificationEvents.newlyDue(emptyMap(), 10, listOf(reminder), 20)
        )
    }

    @Test
    fun newInboxOccurrenceIsReportedOnlyOnce() {
        val inbox = reminder.copy(key = "inbox:a", type = NotificationEventType.INBOX)
        assertEquals(
            listOf(inbox),
            NotificationEvents.newlyDue(emptyMap(), 20, listOf(inbox, inbox), 30)
        )
    }
}
