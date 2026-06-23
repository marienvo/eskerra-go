package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.CreateNoteError
import com.eskerra.go.core.model.CreateNoteException
import com.eskerra.go.core.model.GitStatusSummary
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CreateInboxNoteTest {

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
    fun createsNewInboxNoteUnderInboxDirectory() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "My idea\nBody text")

        assertTrue(result.isSuccess)
        val note = result.getOrThrow().note
        assertTrue(note.path.value.startsWith("Inbox/"))
        assertTrue(note.isInbox)
        assertTrue(note.canEdit)
        assertTrue(File(workspaceDir, note.path.value).exists())
    }

    @Test
    fun createsNewInboxNoteUnderHubInboxDirectory() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "My idea\nBody text", hubFolder = "Daily")

        assertTrue(result.isSuccess)
        val note = result.getOrThrow().note
        assertTrue(note.path.value.startsWith("Daily/Inbox/"))
        assertTrue(note.isInbox)
        assertTrue(note.canEdit)
        assertTrue(File(workspaceDir, note.path.value).exists())
    }

    @Test
    fun writesMarkdownWithH1FromFirstLine() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "Mijn idee\n\nDetails").getOrThrow()

        assertEquals("# Mijn idee\n\nDetails", result.note.markdown)
    }

    @Test
    fun filenameUsesSanitizedTitleFromFirstLine() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "Mijn idee").getOrThrow()

        assertEquals("# Mijn idee\n", result.note.markdown)
        assertEquals("Inbox/Mijn idee.md", result.note.path.value)
    }

    @Test
    fun avoidsFilenameCollisionsWithNumericSuffix() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/Mijn idee.md").writeText("# Existing")

        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "Mijn idee").getOrThrow()

        assertEquals("Inbox/Mijn idee-2.md", result.note.path.value)
    }

    @Test
    fun refreshesRegistryAfterCreate() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val registry = FakeNoteRegistryRepository()
        val cache = NoteRegistryCache(registry)
        cache.refresh(config, filesDir)
        assertEquals(1, registry.refreshCount)
        val writeRepository = FakeNoteWriteRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = cache,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        registry.setResult(
            Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(
                    listOf(
                        NoteSummary(
                            id = NoteId("Inbox/Mijn idee.md"),
                            title = "Mijn idee",
                            snippet = "",
                            isInbox = true,
                            lastModifiedEpochMillis = 1L
                        )
                    )
                )
            )
        )

        useCase(config, filesDir, "Mijn idee")

        assertEquals(2, registry.refreshCount)
        assertTrue(registry.lastPreviousRegistry != null)
    }

    @Test
    fun createRefreshesGitStatus() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository)
        )

        val result = useCase(config, filesDir, "My idea").getOrThrow()

        assertEquals(GitStatusSummary.State.Dirty, result.gitStatus.state)
        assertTrue(result.gitStatus.changedCount >= 1)
    }

    @Test
    fun registryRefreshFailureReturnsTypedError() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val registry = FakeNoteRegistryRepository.failing()
        val useCase = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository(),
            registryCache = NoteRegistryCache(registry),
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository())
        )

        val result = useCase(config, filesDir, "My idea")

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as CreateNoteException).error
        assertTrue(error is CreateNoteError.RegistryRefreshFailed)
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
