package com.eskerra.go.core.notifications

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventMatchingTest {
    private fun scan(markdown: String) = ReminderCandidates.scan("a.md", markdown, ZoneId.of("UTC"))

    @Test
    fun uniqueReminderSurvivesChangingItsLine() {
        val old = scan("Before @2026-11-01_1000").single()
        val current = scan("After @2026-11-01_1000").single()
        assertEquals(
            old,
            NotificationEventMatching.match(mapOf(old.key to old), listOf(current))[current.key]
        )
    }

    @Test
    fun deletingOneDuplicateMatchesTheOtherByContext() {
        val old = scan("First @2026-11-01\nSecond @2026-11-01")
        val current = scan("Second @2026-11-01").single()
        val match = NotificationEventMatching.match(old.associateBy { it.key }, listOf(current))
        assertEquals(old[1], match[current.key])
    }

    @Test
    fun ambiguousDuplicatesAreNeverAssignedToTheRemainingOccurrence() {
        val old = scan("Same @2026-11-01\nSame @2026-11-01")
        val current = scan("Same @2026-11-01").single()
        assertTrue(
            NotificationEventMatching.match(
                old.associateBy {
                    it.key
                },
                listOf(current)
            ).isEmpty()
        )
    }
}
