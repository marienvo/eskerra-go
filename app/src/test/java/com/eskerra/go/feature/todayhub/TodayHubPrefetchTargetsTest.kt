package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayHubPrefetchTargetsTest {

    private fun note(path: String, title: String) =
        NoteSummary(id = NoteId(path), title = title, snippet = "", isInbox = false)

    private fun content(introMarkdown: String, registry: NoteRegistry, row: TodayHubRow? = null) =
        TodayHubUiState.Content(
            hubs = listOf(TodayHubRef(NoteId("Today.md"), "Today")),
            activeHubId = NoteId("Today.md"),
            folderLabel = "",
            introMarkdown = introMarkdown,
            registry = registry,
            columnHeaders = emptyList(),
            selectedWeekStem = "2026-W01",
            weekRangeLabel = "",
            canGoPrev = false,
            canGoNext = false,
            progressSegments = emptyList(),
            row = row,
            rowLoading = false
        )

    @Test
    fun resolvesIntroLinks() {
        val hub = note("Today.md", "Today")
        val target = note("Notes/Target.md", "Target")
        val registry = NoteRegistry.fromNotes(listOf(hub, target))

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "See [[Target]].", registry = registry)
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun resolvesRowColumnLinks_usingRowNoteIdAsSource() {
        val hub = note("Today.md", "Today")
        val rowNote = note("Weeks/2026-W01.md", "Week")
        val target = note("Notes/Target.md", "Target")
        val registry = NoteRegistry.fromNotes(listOf(hub, rowNote, target))
        val row = TodayHubRow(
            rowNoteId = rowNote.id,
            weekStartStem = "2026-W01",
            columns = listOf("[[Target]]", "no links here")
        )

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "", registry = registry, row = row)
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun noRow_resolvesIntroOnly() {
        val hub = note("Today.md", "Today")
        val registry = NoteRegistry.fromNotes(listOf(hub))

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "No links.", registry = registry, row = null)
        )

        assertEquals(emptyList<NoteId>(), result)
    }
}
