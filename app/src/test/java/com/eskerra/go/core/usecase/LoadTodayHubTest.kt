package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadTodayHubTest {

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

    private fun note(path: String): NoteSummary =
        NoteSummary(id = NoteId(path), title = path, snippet = "", isInbox = false)

    private val hubMarkdown = """
        ---
        perpetualType: weekly
        columns:
          - Tasks
        start: monday
        ---
        # Daily hub

        Intro body here.
    """.trimIndent()

    @Test
    fun returnsNullWhenNoHubs() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(note("Inbox/a.md"))
        val content = FakeNoteContentRepository.failing(NoteContentError.NotFound)
        val result = LoadTodayHub(NoteRegistryCache(registry), content)(config, filesDir, null)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun loadsActiveHubSettingsAndIntro() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            note("Work/Today.md"),
            note("Daily/Today.md"),
            note("Daily/2026-04-06.md"),
            note("Daily/2026-03-30.md")
        )
        val content = FakeNoteContentRepository.withContent(NoteId("Daily/Today.md"), hubMarkdown)

        val useCase = LoadTodayHub(NoteRegistryCache(registry), content)
        val data = useCase(config, filesDir, null).getOrThrow()!!

        // First hub by sorted id is Daily/Today.md.
        assertEquals(NoteId("Daily/Today.md"), data.activeHubId)
        assertEquals(listOf("Tasks"), data.settings.columns)
        assertEquals(TodayHubFrontmatter.StartDay.MONDAY, data.settings.start)
        assertEquals(2, data.columnCount)
        assertEquals("Daily", data.folderLabel)
        assertEquals(2, data.hubs.size)
        assertEquals(listOf("2026-03-30", "2026-04-06"), data.availableWeekStems)
        assertTrue(data.introMarkdown.contains("Intro body here."))
        assertTrue(!data.introMarkdown.contains("perpetualType"))
    }

    @Test
    fun honoursPreferredHubWhenPresent() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            note("Work/Today.md"),
            note("Daily/Today.md")
        )
        val content = FakeNoteContentRepository.withContent(NoteId("Work/Today.md"), hubMarkdown)

        val useCase = LoadTodayHub(NoteRegistryCache(registry), content)
        val data = useCase(config, filesDir, NoteId("Work/Today.md")).getOrThrow()!!

        assertEquals(NoteId("Work/Today.md"), data.activeHubId)
        assertEquals("Work", data.folderLabel)
    }

    @Test
    fun propagatesHubReadFailure() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(note("Daily/Today.md"))
        val content = FakeNoteContentRepository.failing(NoteContentError.ReadFailed("io"))
        val result = LoadTodayHub(NoteRegistryCache(registry), content)(config, filesDir, null)
        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.ReadFailed)
    }
}
