package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNoteRegistryRepositoryTest {

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
    fun refresh_withValidWorkspace_returnsScannedNotes() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/hello.md").writeText("# Hello\n\nBody.")

        val repository = FileNoteRegistryRepository()
        val result = repository.refresh(config, filesDir)

        assertTrue(result.isSuccess)
        val inboxNotes = result.getOrThrow().inboxSummaries
        assertEquals(1, inboxNotes.size)
        assertEquals("Hello", inboxNotes.single().title)
    }

    @Test
    fun refresh_withInvalidRelativePath_returnsInvalidWorkspacePath() = runTest {
        val filesDir = temp.newFolder("files")
        val invalidConfig = config.copy(relativePath = "../escape")
        val repository = FileNoteRegistryRepository()

        val result = repository.refresh(invalidConfig, filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteIndexException).error
        assertTrue(error is NoteIndexError.InvalidWorkspacePath)
    }

    @Test
    fun refresh_withMissingWorkspaceDirectory_returnsWorkspaceMissing() = runTest {
        val filesDir = temp.newFolder("files")
        val repository = FileNoteRegistryRepository()

        val result = repository.refresh(config, filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteIndexException).error
        assertTrue(error is NoteIndexError.WorkspaceMissing)
    }

    @Test
    fun refresh_whenScannerFails_returnsScanFailed() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        val failingScanner = object : NoteWorkspaceScanner {
            override fun scan(workspaceDir: File, previousRegistry: NoteRegistry?) =
                Result.failure<NoteRegistry>(RuntimeException("boom"))
        }
        val repository = FileNoteRegistryRepository(scanner = failingScanner)

        val result = repository.refresh(config, filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as NoteIndexException).error
        assertTrue(error is NoteIndexError.ScanFailed)
        assertEquals("boom", (error as NoteIndexError.ScanFailed).detail)
    }
}
