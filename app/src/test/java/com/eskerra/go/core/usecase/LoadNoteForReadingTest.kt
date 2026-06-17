package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadNoteForReadingTest {

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
    fun successReturnsContentMarkdownAndRegistry() = runTest {
        val firstId = NoteId("Inbox/First.md")
        val secondId = NoteId("Second.md")
        val fakeRepo = FakeNoteRegistryRepository.withInboxNotes(
            summary(firstId, "First"),
            summary(secondId, "Second")
        )
        val content = FakeNoteContentRepository.withContent(
            noteId = firstId,
            markdown = "Open [[Second]]."
        )
        val useCase = LoadNoteForReading(NoteRegistryCache(fakeRepo), content)

        val result = useCase(config, filesDir, firstId)

        assertTrue(result.isSuccess)
        val document = result.getOrThrow()
        assertEquals(firstId, document.note.id)
        assertEquals("Open [[Second]].", document.content.markdown)
        // The renderer resolves wiki links against this registry (see VaultReadonlyLinkTest).
        assertTrue(document.registry.notes.any { it.id == secondId })
    }

    @Test
    fun registryRefreshFailureMapsToError() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val fakeRepo = FakeNoteRegistryRepository.failing(NoteIndexError.ScanFailed("boom"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val useCase = LoadNoteForReading(NoteRegistryCache(fakeRepo), content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.ReadFailed)
        assertEquals("boom", (error as NoteContentError.ReadFailed).detail)
    }

    @Test
    fun notFoundWhenNoteMissingFromRegistry() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val fakeRepo = FakeNoteRegistryRepository()
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val useCase = LoadNoteForReading(NoteRegistryCache(fakeRepo), content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.NotFound)
    }

    private fun summary(id: NoteId, title: String): NoteSummary =
        NoteSummary(id = id, title = title, snippet = "", isInbox = id.value.startsWith("Inbox/"))
}
