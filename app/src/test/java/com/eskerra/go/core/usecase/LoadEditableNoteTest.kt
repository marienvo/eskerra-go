package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadEditableNoteTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun inboxNoteIsEditable() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First\n\nBody")
        val useCase = LoadEditableNote(registry, content)

        val result = useCase(config, temp.newFolder("files"), noteId).getOrThrow()

        assertTrue(result.isInbox)
        assertTrue(result.canEdit)
        assertEquals("# First\n\nBody", result.markdown)
    }

    @Test
    fun nonInboxNoteIsReadOnly() = runTest {
        val noteId = NoteId("Projects/Plan.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "Plan", "snippet", isInbox = false)
        )
        val content = FakeNoteContentRepository.withContent(noteId, "# Plan")
        val useCase = LoadEditableNote(registry, content)

        val result = useCase(config, temp.newFolder("files"), noteId).getOrThrow()

        assertFalse(result.isInbox)
        assertFalse(result.canEdit)
    }

    @Test
    fun invalidNoteIdFails() = runTest {
        val useCase = LoadEditableNote(
            FakeNoteRegistryRepository(),
            FakeNoteContentRepository()
        )

        val result = useCase(config, temp.newFolder("files"), NoteId("../secret.md"))

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.InvalidNoteId)
    }

    @Test
    fun missingNoteFails() = runTest {
        val noteId = NoteId("Inbox/Missing.md")
        val useCase = LoadEditableNote(
            FakeNoteRegistryRepository(),
            FakeNoteContentRepository.withContent(noteId, "# Missing")
        )

        val result = useCase(config, temp.newFolder("files"), noteId)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.NotFound)
    }

    private fun summary(id: NoteId, title: String): NoteSummary =
        NoteSummary(id = id, title = title, snippet = "", isInbox = true)
}
