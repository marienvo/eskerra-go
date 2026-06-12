package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.DeleteInboxNoteError
import com.eskerra.go.core.model.DeleteInboxNoteException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeleteInboxNotesTest {

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
    fun deletesInboxFilesFromDisk() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val noteFile = File(workspaceDir, "Inbox/delete-me.md")
        noteFile.parentFile?.mkdirs()
        noteFile.writeText("# Delete me")

        val gitRepository = JGitWorkspaceRepository()
        val useCase = DeleteInboxNotes(
            writeRepository = FileNoteWriteRepository(gitRepository),
            registryRepository = FileNoteRegistryRepository(),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )
        val note = summary(NoteId("Inbox/delete-me.md"))

        val result = useCase(config, filesDir, listOf(note.id), listOf(note))

        assertTrue(result.isSuccess)
        assertFalse(noteFile.exists())
    }

    @Test
    fun rejectsStaleSelection() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val useCase = DeleteInboxNotes(
            writeRepository = FakeNoteWriteRepository(),
            registryRepository = FakeNoteRegistryRepository(),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        val result = useCase(
            config,
            filesDir,
            listOf(NoteId("Inbox/missing.md")),
            emptyList()
        )

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as DeleteInboxNoteException).error
        assertEquals(DeleteInboxNoteError.StaleEntry, error)
    }

    @Test
    fun rejectsNonInboxPath() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val writeRepository = FakeNoteWriteRepository()
        val useCase = DeleteInboxNotes(
            writeRepository = writeRepository,
            registryRepository = FakeNoteRegistryRepository(),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )
        val note = NoteSummary(
            id = NoteId("General/read-only.md"),
            title = "Read only",
            snippet = "",
            isInbox = false
        )

        val result = useCase(config, filesDir, listOf(note.id), listOf(note))

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as DeleteInboxNoteException).error
        assertEquals(DeleteInboxNoteError.NotInInbox, error)
        assertEquals(0, writeRepository.deleteCount)
    }

    private fun summary(id: NoteId) = NoteSummary(
        id = id,
        title = "Title",
        snippet = "",
        isInbox = true
    )

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
