package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteReaderSegment
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
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
    fun resolvedWikiLinksMapToClickablePresentationState() = runTest {
        val firstId = NoteId("Inbox/First.md")
        val secondId = NoteId("Second.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            summary(firstId, "First"),
            summary(secondId, "Second")
        )
        val content = FakeNoteContentRepository.withContent(
            noteId = firstId,
            markdown = "Open [[Second]]."
        )
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, firstId)

        assertTrue(result.isSuccess)
        val segments = result.getOrThrow().segments
        val link = segments.filterIsInstance<NoteReaderSegment.ResolvedLink>().single()
        assertEquals("Second", link.label)
        assertEquals(secondId, link.target)
    }

    @Test
    fun missingWikiLinksMapToNonDestructiveDisplayState() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(
            noteId = noteId,
            markdown = "Missing: [[Missing Note]]."
        )
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isSuccess)
        val link = result.getOrThrow().segments
            .filterIsInstance<NoteReaderSegment.MissingLink>()
            .single()
        assertEquals("Missing Note", link.label)
    }

    @Test
    fun ambiguousWikiLinksMapToNonDestructiveDisplayState() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            summary(noteId, "First"),
            summary(NoteId("Notes/Daily.md"), "Daily"),
            summary(NoteId("Archive/Daily.md"), "Daily")
        )
        val content = FakeNoteContentRepository.withContent(
            noteId = noteId,
            markdown = "Ambiguous: [[Daily]]."
        )
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isSuccess)
        val link = result.getOrThrow().segments
            .filterIsInstance<NoteReaderSegment.AmbiguousLink>()
            .single()
        assertEquals("Daily", link.label)
        assertEquals(2, link.candidateCount)
    }

    @Test
    fun malformedWikiLinksRemainPlainText() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val markdown = "Broken [[unclosed and [[Second]] mixed."
        val registry = FakeNoteRegistryRepository.withInboxNotes(summary(noteId, "First"))
        val content = FakeNoteContentRepository.withContent(noteId, markdown)
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isSuccess)
        val segments = result.getOrThrow().segments
        assertTrue(segments.all { it is NoteReaderSegment.Text })
        assertEquals(markdown, segments.joinToString("") { (it as NoteReaderSegment.Text).text })
    }

    @Test
    fun registryRefreshFailureMapsToError() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.failing(NoteIndexError.ScanFailed("boom"))
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.ReadFailed)
        assertEquals("boom", (error as NoteContentError.ReadFailed).detail)
    }

    @Test
    fun notFoundWhenNoteMissingFromRegistry() = runTest {
        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository()
        val content = FakeNoteContentRepository.withContent(noteId, "# First")
        val useCase = LoadNoteForReading(registry, content)

        val result = useCase(config, filesDir, noteId)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.NotFound)
    }

    private fun summary(id: NoteId, title: String): NoteSummary =
        NoteSummary(id = id, title = title, snippet = "", isInbox = id.value.startsWith("Inbox/"))
}
