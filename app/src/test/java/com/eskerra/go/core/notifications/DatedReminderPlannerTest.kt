package com.eskerra.go.core.notifications

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatedReminderPlannerTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val now = at("2026-11-22T08:00:00")

    @Test
    fun defaultTimeIsSevenNextDayForNewlyRegisteredNotes() {
        val record = record("Flow/Work/2026-11-22.md", firstSeen = at("2026-11-22T06:00:00"))
        val schedules = DatedReminderPlanner.schedules(
            listOf(record),
            emptySet(),
            DatedReminderSettings(),
            at("2026-11-22T06:30:00"),
            zone
        )
        assertEquals(at("2026-11-23T07:00:00"), schedules.single().dueAtEpochMillis)
    }

    @Test
    fun schedulesOnlyOneReminderPerFolderAndSupportsMultipleFolders() {
        val records = listOf(
            record("Flow/Work/2026-11-20.md"),
            record("Flow/Work/2026-11-21.md"),
            record("Flow/Home/2026-11-20.md")
        )
        val schedules = DatedReminderPlanner.schedules(
            records,
            emptySet(),
            DatedReminderSettings(),
            now,
            zone
        )
        assertEquals(listOf("Flow/Home", "Flow/Work"), schedules.map { it.folder })
        assertEquals(
            setOf(at("2026-11-22T07:00:00")),
            schedules.map {
                it.dueAtEpochMillis
            }.toSet()
        )
        assertEquals(
            "Flow/Work/2026-11-21.md",
            DatedReminderPlanner.latestAvailable(
                records,
                "Flow/Work",
                LocalDate.parse("2026-11-22")
            )
                ?.noteId
        )
    }

    @Test
    fun futureNoteBecomesDueOnItsOwnDate() {
        val record = record("Flow/Work/2026-11-25.md")
        val schedules = DatedReminderPlanner.schedules(
            listOf(record),
            emptySet(),
            DatedReminderSettings(),
            now,
            zone
        )
        assertEquals(at("2026-11-25T07:00:00"), schedules.single().dueAtEpochMillis)
    }

    @Test
    fun missedDaysFallAndHandledNoteDoesNotRepeatOnTheSameDay() {
        val record = record("Flow/Work/2026-11-20.md")
        assertEquals(
            at("2026-11-22T07:00:00"),
            DatedReminderPlanner.schedules(
                listOf(record),
                emptySet(),
                DatedReminderSettings(),
                now,
                zone
            ).single().dueAtEpochMillis
        )
        val handled = setOf(DatedReminderHandled(record.noteId, "2026-11-22"))
        val schedules = DatedReminderPlanner.schedules(
            listOf(record),
            handled,
            DatedReminderSettings(),
            now,
            zone
        )
        assertEquals(at("2026-11-23T07:00:00"), schedules.single().dueAtEpochMillis)
    }

    @Test
    fun timeChangesAndDisableTakeEffectOnTheNextCalculation() {
        val record = record("Flow/Work/2026-11-20.md")
        val settings = DatedReminderSettings(minuteOfDay = 18 * 60 + 15)
        assertEquals(
            at("2026-11-22T18:15:00"),
            DatedReminderPlanner.schedules(listOf(record), emptySet(), settings, now, zone)
                .single().dueAtEpochMillis
        )
        assertTrue(
            DatedReminderPlanner.schedules(
                listOf(record),
                emptySet(),
                settings.copy(enabled = false),
                now,
                zone
            ).isEmpty()
        )
    }

    @Test
    fun localTimeSurvivesSummerAndWinterTime() {
        val record = record(
            "Flow/Work/2026-07-01.md",
            firstSeen = at("2026-01-01T08:00:00")
        )
        val summer = DatedReminderPlanner.schedules(
            listOf(record),
            emptySet(),
            DatedReminderSettings(),
            at("2026-07-01T08:00:00"),
            zone
        ).single().dueAtEpochMillis
        val winter = DatedReminderPlanner.schedules(
            listOf(record),
            emptySet(),
            DatedReminderSettings(),
            now,
            zone
        ).single().dueAtEpochMillis
        assertEquals(at("2026-07-01T07:00:00"), summer)
        assertEquals(at("2026-11-22T07:00:00"), winter)
    }

    private fun record(noteId: String, firstSeen: Long = at("2026-11-01T08:00:00")) =
        DatedNoteRecord(
            noteId,
            noteId.substringBeforeLast('/'),
            noteId.substringAfterLast('/').removeSuffix(".md"),
            firstSeen,
            "Reminder"
        )

    private fun at(value: String): Long = LocalDateTime.parse(value).atZone(zone)
        .toInstant().toEpochMilli()
}
