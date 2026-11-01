package com.eskerra.go.core.notifications

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCandidatesTest {
    @Test
    fun parsesDesktopShapesAndIgnoresStruckAndInvalidTokens() {
        val markdown = "@2026-11-01 @2026-11-01_1200-1300 @~~2026-11-01~~ @2026-11-01_9999"
        val actual = ReminderCandidates.scan("Inbox/a.md", markdown, ZoneId.of("UTC"))
        assertEquals(2, actual.size)
        assertEquals(
            Instant.parse("2026-11-01T09:00:00Z").toEpochMilli(),
            actual[0].dueAtEpochMillis
        )
        assertEquals(
            Instant.parse("2026-11-01T12:00:00Z").toEpochMilli(),
            actual[1].dueAtEpochMillis
        )
    }

    @Test
    fun duplicateTokensHaveDistinctStableIdentities() {
        val markdown = "@2026-11-01 @2026-11-01"
        val actual = ReminderCandidates.scan("a.md", markdown, ZoneId.of("UTC"))
        assertEquals(2, actual.map { it.key }.toSet().size)
        assertEquals(actual, ReminderCandidates.scan("a.md", markdown, ZoneId.of("UTC")))
    }

    @Test
    fun recognizesUnicodeWhitespaceAndRejectsInvalidRanges() {
        val actual = ReminderCandidates.scan(
            "a.md",
            "x\u00a0@2026-11-01_1000-2500 @2026-11-01_1100",
            ZoneId.of("UTC")
        )
        assertEquals(1, actual.size)
        assertEquals(
            Instant.parse("2026-11-01T11:00:00Z").toEpochMilli(),
            actual[0].dueAtEpochMillis
        )
    }

    @Test
    fun removingAnEarlierDuplicateKeepsTheOtherLineIdentity() {
        val before = ReminderCandidates.scan(
            "a.md",
            "First @2026-11-01\nSecond @2026-11-01",
            ZoneId.of("UTC")
        )
        val after = ReminderCandidates.scan("a.md", "Second @2026-11-01", ZoneId.of("UTC"))
        val matches = NotificationEventMatching.match(before.associateBy { it.key }, after)
        assertEquals(before[1], matches[after.single().key])
    }

    @Test
    fun ambiguousIdenticalLinesAreReidentifiedWhenCountChanges() {
        val before = ReminderCandidates.scan(
            "a.md",
            "Same @2026-11-01\nSame @2026-11-01",
            ZoneId.of("UTC")
        )
        val after = ReminderCandidates.scan("a.md", "Same @2026-11-01", ZoneId.of("UTC"))
        assertEquals(false, before.any { it.key == after.single().key })
    }
}
