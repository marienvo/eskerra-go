package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNoteContentRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val repository = FileNoteContentRepository()

    @Test
    fun loadsNoteContentByValidNoteId() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        val expected = "# First\n\nOpen [[Second]]."
        File(workspaceDir, "Inbox/First.md").writeText(expected)

        val result = repository.load(config, filesDir, NoteId("Inbox/First.md"))

        assertTrue(result.isSuccess)
        val content = result.getOrThrow()
        assertEquals(NoteId("Inbox/First.md"), content.id)
        assertEquals("Inbox/First.md", content.path.value)
        assertEquals(expected, content.markdown)
    }

    @Test
    fun rejectsPathTraversal() = runTest {
        val filesDir = temp.newFolder("files")
        workspace(filesDir)

        listOf(
            NoteId("../secret.md"),
            NoteId("Inbox/../secret.md"),
            NoteId("..\\secret.md")
        ).forEach { noteId ->
            val result = repository.load(config, filesDir, noteId)
            assertTrue("Expected failure for $noteId", result.isFailure)
            val error = (result.exceptionOrNull() as NoteContentException).error
            assertTrue(error is NoteContentError.InvalidNoteId)
        }
    }

    @Test
    fun returnsNotFoundForMissingFile() = runTest {
        val filesDir = temp.newFolder("files")
        workspace(filesDir)

        val result = repository.load(config, filesDir, NoteId("Inbox/Missing.md"))

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(error is NoteContentError.NotFound)
    }

    @Test
    fun readsNestedNotePaths() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        File(workspaceDir, "Projects/App").mkdirs()
        val expected = "# Plan\n\nNested note."
        File(workspaceDir, "Projects/App/Plan.md").writeText(expected)

        val result = repository.load(config, filesDir, NoteId("Projects/App/Plan.md"))

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrThrow().markdown)
    }

    @Test
    fun normalizesWorkspaceRelativePathDeterministically() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/First.md").writeText("# First")

        val result = repository.load(config, filesDir, NoteId("Inbox\\First.md"))

        assertTrue(result.isSuccess)
        assertEquals("Inbox/First.md", result.getOrThrow().path.value)
    }

    @Test
    fun rejectsSymlinkEscapingWorkspace_ifSupported() = runTest {
        assumeTrue("Symlinks not supported on this platform", supportsSymlinks())

        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        val outsideDir = temp.newFolder("outside")
        val outsideFile = File(outsideDir, "secret.md")
        outsideFile.writeText("# Secret")

        val linkPath = File(workspaceDir, "Inbox/link.md")
        File(workspaceDir, "Inbox").mkdirs()
        Files.createSymbolicLink(linkPath.toPath(), outsideFile.toPath())

        val result = repository.load(config, filesDir, NoteId("Inbox/link.md"))

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteContentException).error
        assertTrue(
            error is NoteContentError.InvalidNoteId || error is NoteContentError.NotFound
        )
    }

    private fun workspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        return dir
    }

    private fun supportsSymlinks(): Boolean = try {
        val link = temp.newFolder("link-test").toPath().resolve("link")
        val target = temp.newFolder("link-target").toPath().resolve("target.txt")
        Files.createFile(target)
        Files.createSymbolicLink(link, target)
        Files.deleteIfExists(link)
        true
    } catch (_: Exception) {
        false
    }
}
