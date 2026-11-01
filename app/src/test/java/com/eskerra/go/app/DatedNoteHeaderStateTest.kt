package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.todayhub.DatedNoteHeader
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DatedNoteHeaderStateTest {
    private val current = NoteId("Daily/2026-11-01.md")
    private val previous = NoteId("Daily/2026-10-31.md")
    private val next = NoteId("Daily/2026-11-02.md")
    private val linked = NoteId("2026/W44.md")
    private val header = DatedNoteHeader(
        links = listOf(TodayHubDatedLink("2026", linked, isIncomplete = false)),
        current = current,
        previous = previous,
        next = next,
        dateLabel = "Today"
    )

    @Test
    fun datedHeaderForNavigation_datedTargets_retainPreviousWhileLoading() {
        listOf(previous, next, linked).forEach { target ->
            assertSame(header, datedHeaderForNavigation(null, header, target))
        }
    }

    @Test
    fun datedHeaderForNavigation_knownDatedTargetFromAnotherHub_retainsPreviousWhileLoading() {
        val target = NoteId("Other/2026-11-03.md")

        assertSame(header, datedHeaderForNavigation(null, header, target, targetIsDated = true))
    }

    @Test
    fun datedHeaderForNavigation_nonDatedTarget_doesNotRetainPrevious() {
        assertNull(datedHeaderForNavigation(null, header, NoteId("Inbox/note.md")))
    }

    @Test
    fun datedHeaderForNavigation_resolvedHeader_replacesPreviousImmediately() {
        val resolved = header.copy(current = next, dateLabel = "2026-11-02")

        assertSame(resolved, datedHeaderForNavigation(resolved, header, next))
    }
}
