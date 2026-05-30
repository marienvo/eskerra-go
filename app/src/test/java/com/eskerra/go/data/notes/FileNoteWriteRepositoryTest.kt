package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNoteWriteRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val gitRepository = JGitWorkspaceRepository()
    private val repository = FileNoteWriteRepository(gitRepository)

    @Test
    fun writesValidInboxNoteAsUtf8() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val notePath = NotePath.fromRelativePath("Inbox/First.md").getOrThrow()
        val markdown = "# First\n\nUnicode: café"

        val result = repository.write(config, filesDir, notePath, markdown)

        assertTrue(result.isSuccess)
        assertEquals(markdown, File(workspaceDir, "Inbox/First.md").readText(Charsets.UTF_8))
    }

    @Test
    fun rejectsAbsolutePath() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)

        val notePath = NotePath.fromRelativePath("/etc/passwd").getOrElse {
            assertTrue(it.message?.contains("relative") == true)
            return@runTest
        }

        val result = repository.write(config, filesDir, notePath, "# Nope")
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsPathTraversal() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)

        listOf(
            "../secret.md",
            "Inbox/../secret.md",
            "..\\secret.md"
        ).forEach { raw ->
            val notePathResult = NotePath.fromRelativePath(raw)
            if (notePathResult.isFailure) {
                return@forEach
            }
            val result = repository.write(
                config,
                filesDir,
                notePathResult.getOrThrow(),
                "# Secret"
            )
            assertTrue("Expected failure for $raw", result.isFailure)
            val error = (result.exceptionOrNull() as? NoteWriteException)?.error
            assertTrue(
                error is NoteWriteError.WriteFailed || error is NoteWriteError.InvalidNotePath
            )
        }
    }

    @Test
    fun rejectsMissingWorkspace() = runTest {
        val filesDir = temp.newFolder("files")
        val notePath = NotePath.fromRelativePath("Inbox/First.md").getOrThrow()

        val result = repository.write(config, filesDir, notePath, "# First")

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteWriteException).error
        assertTrue(error is NoteWriteError.WorkspaceMissing)
    }

    @Test
    fun existsReturnsFalseForMissingFile() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val notePath = NotePath.fromRelativePath("Inbox/Missing.md").getOrThrow()

        val result = repository.exists(config, filesDir, notePath)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun existsReturnsTrueForExistingFile() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/First.md").writeText("# First")
        val notePath = NotePath.fromRelativePath("Inbox/First.md").getOrThrow()

        val result = repository.exists(config, filesDir, notePath)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        gitRepository.initOrOpen(dir).getOrThrow()
        return dir
    }
}
