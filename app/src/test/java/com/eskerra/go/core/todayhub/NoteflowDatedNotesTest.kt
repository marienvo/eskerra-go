package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteflowDatedNotesTest {
    @Test
    fun selectsCurrentOrEarliestFutureInDirectVisibleFolders() {
        val paths = listOf(
            "Work/2026-10-26.md", "Work/Journal/2026-10-27.md",
            "Work/Journal/2026-11-01.md", "Work/Journal/2026-11-03.md",
            "Work/Plans/2026-11-04.md", "Work/Plans/2026-11-02.md",
            "Work/Inbox/2026-10-30.md", "Work/.hidden/2026-10-30.md",
            "Work/Journal/2026-02-30.md", "Work/Journal/Nested/2026-10-30.md"
        )
        val registry = NoteRegistry.fromNotes(paths.map { NoteSummary(NoteId(it), "", "", false) })
        val result = NoteflowDatedNotes.select(
            registry,
            NoteId("Work/.flow"),
            LocalDate(2026, 11, 1)
        )
        assertEquals(listOf("Journal", "Plans"), result.map { it.folder })
        assertEquals(
            listOf("Work/Journal/2026-11-01.md", "Work/Plans/2026-11-02.md"),
            result.map { it.noteId.value }
        )
    }

    @Test
    fun completionDependsOnlyOnFirstHeading() {
        assertTrue(NoteflowDatedNotes.isIncomplete("# Title\n\n✅"))
        assertTrue(NoteflowDatedNotes.isIncomplete("no title"))
        assertFalse(NoteflowDatedNotes.isIncomplete("# Done ✅️\n"))
    }
}
