package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DatedNoteNotificationCandidatesTest {
    @Test
    fun registersEveryNewNoteInTheSameFolder() {
        val first = NoteId("Flow/Work/2026-10-31.md")
        val second = NoteId("Flow/Work/2026-11-01.md")
        val registry = NoteRegistry.fromNotes(
            listOf(first, second).map { NoteSummary(it, "", "", false) }
        )
        val actual = DatedNoteNotificationCandidates.select(
            registry,
            listOf(NoteId("Flow/.flow")),
            LocalDate(2026, 11, 1),
            mapOf(first to "# First", second to "# Second")
        )
        assertEquals(listOf(first.value, second.value), actual.map { it.noteId })
        assertEquals(setOf(0L), actual.map { it.dueAtEpochMillis }.toSet())
    }

    @Test
    fun excludesFutureAndCompletedDatedNotes() {
        val current = NoteId("Flow/Work/2026-11-01.md")
        val future = NoteId("Flow/Home/2026-11-02.md")
        val completed = NoteId("Flow/Done/2026-11-01.md")
        val registry = NoteRegistry.fromNotes(
            listOf(current, future, completed).map {
                NoteSummary(it, "", "", false)
            }
        )
        val content = mapOf(current to "# Work", future to "# Home", completed to "# ✅ Done")
        val actual = DatedNoteNotificationCandidates.select(
            registry,
            listOf(NoteId("Flow/.flow")),
            LocalDate(2026, 11, 1),
            content
        )
        assertEquals(
            listOf(current.value),
            actual.filter {
                it.dueAtEpochMillis == 0L
            }.map { it.noteId }
        )
        assertEquals(
            Long.MAX_VALUE,
            actual.single {
                it.noteId == completed.value
            }.dueAtEpochMillis
        )
        assertEquals(
            NotificationEventType.FUTURE_DATED_NOTE,
            actual.single { it.noteId == future.value }.type
        )
    }

    @Test
    fun carriesFrontmatterMessageAndFilenameFallback() {
        val first = NoteId("Flow/Work/2026-11-01.md")
        val second = NoteId("Flow/Home/2026-11-01.md")
        val registry = NoteRegistry.fromNotes(
            listOf(first, second).map { NoteSummary(it, "", "", false) }
        )
        val actual = DatedNoteNotificationCandidates.select(
            registry,
            listOf(NoteId("Flow/.flow")),
            LocalDate(2026, 11, 1),
            mapOf(
                first to "---\nnotification: Focus today\n---\n# Work",
                second to "---\nnotification: false\n---\n# Home"
            )
        )
        assertEquals(
            mapOf(first.value to "Focus today", second.value to "2026-11-01"),
            actual.associate { it.noteId to it.message }
        )
    }
}
