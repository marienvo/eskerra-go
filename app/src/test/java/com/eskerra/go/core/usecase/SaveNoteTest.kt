package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.GitStatusSummary
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.SaveNoteError
import com.eskerra.go.core.model.SaveNoteException
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveNoteTest {

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
    fun writesFullUtf8MarkdownToInboxNote() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/First.md").writeText("# First\n\nOld body")

        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = SaveNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val updated = "# First\n\nUpdated body"
        val result = useCase(config, filesDir, NoteId("Inbox/First.md"), updated).getOrThrow()

        assertEquals(updated, result.note.markdown)
        assertEquals(updated, File(workspaceDir, "Inbox/First.md").readText())
    }

    @Test
    fun rejectsPathTraversal() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val useCase = SaveNote(
            writeRepository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryRepository = FakeNoteRegistryRepository.withInboxNotes(
                NoteSummary(NoteId("Inbox/First.md"), "First", "", isInbox = true)
            ),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        listOf(
            NoteId("../secret.md"),
            NoteId("Inbox/../secret.md"),
            NoteId("..\\secret.md")
        ).forEach { noteId ->
            val result = useCase(config, filesDir, noteId, "# Secret")
            assertTrue("Expected failure for $noteId", result.isFailure)
            val error = (result.exceptionOrNull() as SaveNoteException).error
            assertTrue(error is SaveNoteError.InvalidNoteId)
        }
    }

    @Test
    fun rejectsNonInboxNoteFromRegistryMetadata() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val noteId = NoteId("Projects/Plan.md")
        val useCase = SaveNote(
            writeRepository = FakeNoteWriteRepository(),
            registryRepository = FakeNoteRegistryRepository.withInboxNotes(
                NoteSummary(noteId, "Plan", "", isInbox = false)
            ),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        val result = useCase(config, filesDir, noteId, "# Plan")

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as SaveNoteException).error
        assertTrue(error is SaveNoteError.ReadOnlyNote)
    }

    @Test
    fun rejectsMissingNoteFromRefreshedRegistry() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val useCase = SaveNote(
            writeRepository = FakeNoteWriteRepository(),
            registryRepository = FakeNoteRegistryRepository(),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        val result = useCase(config, filesDir, NoteId("Inbox/Missing.md"), "# Missing")

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as SaveNoteException).error
        assertTrue(error is SaveNoteError.NotFound)
    }

    @Test
    fun saveRefreshesRegistryTwice() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/First.md").writeText("# First")

        val noteId = NoteId("Inbox/First.md")
        val registry = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "First", "", isInbox = true)
        )
        val useCase = SaveNote(
            writeRepository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryRepository = registry,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        useCase(config, filesDir, noteId, "# First\n\nSaved")

        assertEquals(2, registry.refreshCount)
    }

    @Test
    fun saveRefreshesGitStatus() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/First.md").writeText("# First")

        val gitRepository = JGitWorkspaceRepository()
        val useCase = SaveNote(
            writeRepository = FileNoteWriteRepository(gitRepository),
            registryRepository = FileNoteRegistryRepository(),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(
            config,
            filesDir,
            NoteId("Inbox/First.md"),
            "# First\n\nUpdated"
        ).getOrThrow()

        assertEquals(GitStatusSummary.State.Dirty, result.gitStatus.state)
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
