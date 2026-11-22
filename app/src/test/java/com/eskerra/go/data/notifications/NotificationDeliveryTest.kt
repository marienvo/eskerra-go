package com.eskerra.go.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationDeliveryTest {
    @Test
    fun datedNoteIdsAreStableAndDistinctPerNote() {
        val first = datedNotificationId("Flow/Work/2026-11-21.md")
        val second = datedNotificationId("Flow/Work/2026-11-22.md")
        assertEquals(first, datedNotificationId("Flow/Work/2026-11-21.md"))
        assertNotEquals(first, second)
    }
}
