package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationOpenRequestTest {
    @Test
    fun notificationWaitsForHomeToSettle() {
        val note = NoteId("Flow/Work/2026-11-24.md")
        val request = NotificationOpenRequest(listOf(note.value), requestId = 1)

        assertNull(request.destination(launchSettled = false, handledRequestId = null))
        assertEquals(
            AppRoute.notificationNote(note),
            request.destination(launchSettled = true, handledRequestId = null)
        )
    }

    @Test
    fun groupedNotificationWaitsForHomeAndOpensOnlyOnce() {
        val request = NotificationOpenRequest(
            listOf("Flow/Work/2026-11-24.md", "Flow/Work/2026-11-25.md"),
            requestId = 1
        )

        assertNull(request.destination(launchSettled = false, handledRequestId = null))
        assertEquals(
            AppRoute.NOTIFICATION_LIST,
            request.destination(launchSettled = true, handledRequestId = null)
        )
        assertNull(request.destination(launchSettled = true, handledRequestId = request.requestId))
    }

    @Test
    fun sameNoteGetsANewRequestForEveryNotificationTap() {
        val initial = NotificationOpenRequest(emptyList())
        val first = NotificationOpenRequest.next(listOf("Flow/Work/today.md"), initial)
        val second = NotificationOpenRequest.next(first.noteIds, first)
        assertNotEquals(first, second)
        assertNull(first.destination(launchSettled = true, handledRequestId = first.requestId))
        assertEquals(
            AppRoute.notificationNote(NoteId(second.noteIds.single())),
            second.destination(launchSettled = true, handledRequestId = first.requestId)
        )
    }
}
