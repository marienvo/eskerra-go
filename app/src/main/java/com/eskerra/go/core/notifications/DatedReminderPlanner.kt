package com.eskerra.go.core.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DatedReminderPlanner {
    fun schedules(
        records: List<DatedNoteRecord>,
        handled: Set<DatedReminderHandled>,
        settings: DatedReminderSettings,
        nowEpochMillis: Long,
        zone: ZoneId
    ): List<DatedReminderSchedule> {
        if (!settings.enabled) return emptyList()
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        return records.groupBy { it.folder }.mapNotNull { (folder, folderRecords) ->
            val sorted = folderRecords.sortedBy { it.date }
            val candidate = sorted.lastOrNull {
                LocalDate.parse(it.date) <= today
            } ?: sorted.first()
            val firstReminderDay = candidate.firstAvailableAtEpochMillis?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().plusDays(1)
            } ?: LocalDate.parse(candidate.date).plusDays(1)
            var day = maxOf(today, LocalDate.parse(candidate.date), firstReminderDay)
            if (DatedReminderHandled(candidate.noteId, day.toString()) in handled) {
                day = day.plusDays(1)
            }
            val due = day.atTime(settings.hour, settings.minute).atZone(zone).toInstant()
            DatedReminderSchedule(folder, due.toEpochMilli())
        }.sortedBy { it.folder }
    }

    fun latestAvailable(
        records: List<DatedNoteRecord>,
        folder: String,
        today: LocalDate
    ): DatedNoteRecord? = records.filter {
        it.folder == folder && LocalDate.parse(it.date) <= today
    }.maxByOrNull { it.date }
}
