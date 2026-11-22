package com.eskerra.go.app

import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationOpenRequestTest {
    @Test
    fun sameNoteGetsANewRequestForEveryNotificationTap() {
        val initial = NotificationOpenRequest(emptyList())
        val first = NotificationOpenRequest.next(listOf("Flow/Work/today.md"), initial)
        val second = NotificationOpenRequest.next(first.noteIds, first)
        assertNotEquals(first, second)
    }
}
