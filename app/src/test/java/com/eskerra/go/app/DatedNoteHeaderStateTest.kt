package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.todayhub.DatedNoteHeader
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import com.eskerra.go.core.todayhub.TodayHubRef
import org.junit.Assert.assertEquals
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

    @Test
    fun datedHeaderForNavigation_notificationOpen_doesNotRetainPreviousHub() {
        assertNull(
            datedHeaderForNavigation(
                null,
                header,
                current,
                targetIsDated = true,
                retainPrevious = false
            )
        )
    }

    @Test
    fun datedNoteMarker_resolvesOwningHubAndIgnoresNonDatedNotes() {
        val work = NoteId("Work/.flow")
        val home = NoteId("Home/.flow")
        val dated = NoteId("Home/Daily/2026-11-01.md")
        val registry = NoteRegistry.fromNotes(
            listOf(NoteSummary(dated, "Daily", "", false))
        )
        val hubs = listOf(TodayHubRef(work, "Work"), TodayHubRef(home, "Home"))

        assertEquals(home, datedNoteMarker(hubs, registry, dated))
        assertNull(datedNoteMarker(hubs, registry, NoteId("Home/Inbox/note.md")))
        assertNull(datedNoteMarker(hubs, registry, NoteId("Home/Daily/missing.md")))
    }
}
