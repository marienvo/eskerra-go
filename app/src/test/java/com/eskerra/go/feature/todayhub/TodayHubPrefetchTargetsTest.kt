package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.data.notes.ParsedMarkdownCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayHubPrefetchTargetsTest {

    private val parsedMarkdownCache = ParsedMarkdownCache()

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
    fun resolvesIntroLinks() = runTest {
        val hub = note("Today.md", "Today")
        val target = note("Notes/Target.md", "Target")
        val registry = NoteRegistry.fromNotes(listOf(hub, target))

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "See [[Target]].", registry = registry),
            parsedMarkdownCache
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun resolvesRowColumnLinks_usingRowNoteIdAsSource() = runTest {
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
            content(introMarkdown = "", registry = registry, row = row),
            parsedMarkdownCache
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun noRow_resolvesIntroOnly() = runTest {
        val hub = note("Today.md", "Today")
        val registry = NoteRegistry.fromNotes(listOf(hub))

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "No links.", registry = registry, row = null),
            parsedMarkdownCache
        )

        assertEquals(emptyList<NoteId>(), result)
    }

    @Test
    fun blankIntroAndBlankColumn_areSkipped() = runTest {
        val hub = note("Today.md", "Today")
        val rowNote = note("Weeks/2026-W01.md", "Week")
        val registry = NoteRegistry.fromNotes(listOf(hub, rowNote))
        val row = TodayHubRow(
            rowNoteId = rowNote.id,
            weekStartStem = "2026-W01",
            columns = listOf("", "   ")
        )

        val result = TodayHubPrefetchTargets.resolve(
            content(introMarkdown = "", registry = registry, row = row),
            parsedMarkdownCache
        )

        assertEquals(emptyList<NoteId>(), result)
    }
}
