package com.eskerra.go.core.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayHubDiscoveryTest {

    private fun note(path: String): NoteSummary =
        NoteSummary(id = NoteId(path), title = path, snippet = "", isInbox = false)

    private fun registry(vararg paths: String): NoteRegistry =
        NoteRegistry.fromNotes(paths.map(::note))

    @Test
    fun isTodayHubNote() {
        assertTrue(TodayHubDiscovery.isTodayHubNote("Daily/Today.md"))
        assertTrue(TodayHubDiscovery.isTodayHubNote("Today.md"))
        assertFalse(TodayHubDiscovery.isTodayHubNote("Daily/Todayish.md"))
        assertFalse(TodayHubDiscovery.isTodayHubNote("Daily/2026-04-06.md"))
    }

    @Test
    fun directoryOf() {
        assertEquals("Daily", TodayHubDiscovery.directoryOf("Daily/Today.md"))
        assertEquals("a/b", TodayHubDiscovery.directoryOf("a/b/Today.md"))
        assertEquals("", TodayHubDiscovery.directoryOf("Today.md"))
    }

    @Test
    fun folderLabelUsesParentFolder() {
        assertEquals("Daily", TodayHubDiscovery.folderLabel("Daily/Today.md"))
        assertEquals("Planning", TodayHubDiscovery.folderLabel("Work/Planning/Today.md"))
        assertEquals("Today", TodayHubDiscovery.folderLabel("Today.md"))
    }

    @Test
    fun sortedHubNoteIdsAreStable() {
        val registry = registry(
            "Work/Today.md",
            "Inbox/note.md",
            "Daily/Today.md",
            "Daily/2026-04-06.md"
        )
        assertEquals(
            listOf(NoteId("Daily/Today.md"), NoteId("Work/Today.md")),
            TodayHubDiscovery.sortedHubNoteIds(registry)
        )
    }

    @Test
    fun rowNoteIdSitsBesideHub() {
        assertEquals(
            NoteId("Daily/2026-04-06.md"),
            TodayHubDiscovery.rowNoteId("Daily/Today.md", "2026-04-06")
        )
        assertEquals(
            NoteId("2026-04-06.md"),
            TodayHubDiscovery.rowNoteId("Today.md", "2026-04-06")
        )
    }

    @Test
    fun availableWeekStemsCollectsSiblingRows() {
        val registry = registry(
            "Daily/Today.md",
            "Daily/2026-04-06.md",
            "Daily/2026-03-30.md",
            "Daily/notes.md",
            "Other/2026-04-13.md"
        )
        assertEquals(
            listOf("2026-03-30", "2026-04-06"),
            TodayHubDiscovery.availableWeekStems("Daily/Today.md", registry)
        )
    }
}
