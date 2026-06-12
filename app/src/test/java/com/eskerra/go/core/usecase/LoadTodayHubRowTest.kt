package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadTodayHubRowTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val filesDir get() = temp.newFolder("files")

    @Test
    fun splitsExistingRowIntoColumns() = runTest {
        val rowId = NoteId("Daily/2026-04-06.md")
        val content = FakeNoteContentRepository.withContent(
            rowId,
            "default col\n\n::today-section::\n\ntask col"
        )
        val row = LoadTodayHubRow(content)(
            config,
            filesDir,
            todayNoteId = NoteId("Daily/Today.md"),
            weekStartStem = "2026-04-06",
            columnCount = 2
        ).getOrThrow()

        assertEquals(rowId, row.rowNoteId)
        assertEquals(listOf("default col", "task col"), row.columns)
    }

    @Test
    fun missingRowYieldsEmptyColumns() = runTest {
        val content = FakeNoteContentRepository.failing(NoteContentError.NotFound)
        val row = LoadTodayHubRow(content)(
            config,
            filesDir,
            todayNoteId = NoteId("Daily/Today.md"),
            weekStartStem = "2026-04-13",
            columnCount = 3
        ).getOrThrow()

        assertEquals(listOf("", "", ""), row.columns)
        assertEquals(NoteId("Daily/2026-04-13.md"), row.rowNoteId)
    }
}
