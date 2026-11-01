package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteflowRepository
import com.eskerra.go.core.todayhub.NoteflowConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadTodayHubTest {
    private val config = WorkspaceConfig("test", "vault", null, "main", 0)
    private val marker = NoteId("Work/.flow")
    private val flow = NoteflowConfig(
        marker,
        "Work title",
        listOf("[[Plan]]"),
        TodayHubFrontmatter.Settings(columns = listOf("Tasks"))
    )
    private fun note(path: String) = NoteSummary(NoteId(path), "", "", false)

    @Test
    fun loadsMarkerAndCurrentDatedNote() = runTest {
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            note("Work/2026-11-02.md"),
            note("Work/Journal/2026-10-30.md")
        )
        val content = FakeNoteContentRepository.withContent(
            NoteId("Work/Journal/2026-10-30.md"),
            "# Done ✅"
        )
        val useCase = LoadTodayHub(
            NoteRegistryCache(registry),
            content,
            NoteflowRepository { _, _ ->
                Result.success(listOf(flow))
            },
            today = { LocalDate(2026, 11, 1) }
        )
        val result = useCase(config, File("."), NoteId("Work/Today.md")).getOrThrow()!!
        assertEquals(marker, result.activeHubId)
        assertEquals("Work title", result.folderLabel)
        assertEquals("[[Plan]]", result.introMarkdown)
        assertEquals(listOf("2026-11-02"), result.availableWeekStems)
        assertEquals(false, result.datedLinks.single().isIncomplete)
    }

    @Test
    fun emptyAndInvalidMarkersAreReported() = runTest {
        val registry = NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes())
        val content = FakeNoteContentRepository.withContent(NoteId("unused.md"), "")
        val empty =
            LoadTodayHub(
                registry,
                content,
                NoteflowRepository { _, _ ->
                    Result.success(emptyList())
                }
            )
        assertNull(empty(config, File("."), null).getOrThrow())
        val invalid =
            LoadTodayHub(
                registry,
                content,
                NoteflowRepository { _, _ ->
                    Result.failure(Exception("bad"))
                }
            )
        assertTrue(invalid(config, File("."), null).isFailure)
    }

    @Test
    fun prefersSelectedFlowAndPropagatesRegistryFailure() = runTest {
        val second = flow.copy(id = NoteId("Other/.flow"), title = "Other")
        val content = FakeNoteContentRepository.withContent(NoteId("unused.md"), "")
        val flows = NoteflowRepository { _, _ -> Result.success(listOf(flow, second)) }
        val useCase =
            LoadTodayHub(
                NoteRegistryCache(FakeNoteRegistryRepository.withInboxNotes()),
                content,
                flows
            )
        assertEquals(second.id, useCase(config, File("."), second.id).getOrThrow()?.activeHubId)

        val broken = LoadTodayHub(
            NoteRegistryCache(
                FakeNoteRegistryRepository.failing(NoteIndexError.WorkspaceMissing(null))
            ),
            content,
            flows
        )
        val error = broken(config, File("."), null).exceptionOrNull() as NoteContentException
        assertEquals(NoteContentError.WorkspaceMissing, error.error)
    }
}
