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
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

    private val fixedInstant = Instant.parse("2026-05-30T16:42:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun createsNewInboxNoteUnderInboxDirectory() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository),
            clock = fixedClock
        )

        val result = useCase(config, filesDir)

        assertTrue(result.isSuccess)
        val note = result.getOrThrow().note
        assertTrue(note.path.value.startsWith("Inbox/"))
        assertTrue(note.isInbox)
        assertTrue(note.canEdit)
        assertTrue(File(workspaceDir, note.path.value).exists())
    }

    @Test
    fun writesDefaultUtf8Markdown() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository),
            clock = fixedClock
        )

        val result = useCase(config, filesDir).getOrThrow()
        val expectedTitle = CreateInboxNote.defaultTitle(fixedInstant, ZoneOffset.UTC)
        assertEquals("# $expectedTitle\n\n", result.note.markdown)
    }

    @Test
    fun generatedFilenameUsesInjectedClock() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository),
            clock = fixedClock
        )

        val result = useCase(config, filesDir).getOrThrow()

        assertEquals("Inbox/2026-05-30-164200.md", result.note.path.value)
    }

    @Test
    fun avoidsFilenameCollisionsWithNumericSuffix() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/2026-05-30-164200.md").writeText("# Existing")

        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository),
            clock = fixedClock
        )

        val result = useCase(config, filesDir).getOrThrow()

        assertEquals("Inbox/2026-05-30-164200-2.md", result.note.path.value)
    }

    @Test
    fun refreshesRegistryAfterCreate() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val registry = FakeNoteRegistryRepository()
        val writeRepository = FakeNoteWriteRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registry,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository()),
            clock = fixedClock
        )

        registry.setResult(
            Result.success(
                com.eskerra.go.core.model.NoteRegistry.fromNotes(
                    listOf(
                        NoteSummary(
                            id = NoteId("Inbox/2026-05-30-164200.md"),
                            title = "Untitled inbox note",
                            snippet = "",
                            isInbox = true
                        )
                    )
                )
            )
        )

        useCase(config, filesDir)

        assertEquals(1, registry.refreshCount)
    }

    @Test
    fun createRefreshesGitStatus() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val registryRepository = FileNoteRegistryRepository()
        val useCase = CreateInboxNote(
            writeRepository = writeRepository,
            registryRepository = registryRepository,
            loadGitStatusSummary = LoadGitStatusSummary(gitRepository),
            clock = fixedClock
        )

        val result = useCase(config, filesDir).getOrThrow()

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
            registryRepository = registry,
            loadGitStatusSummary = LoadGitStatusSummary(JGitWorkspaceRepository()),
            clock = fixedClock
        )

        val result = useCase(config, filesDir)

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
