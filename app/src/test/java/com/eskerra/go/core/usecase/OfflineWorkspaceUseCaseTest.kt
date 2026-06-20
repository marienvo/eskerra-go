package com.eskerra.go.core.usecase

import com.eskerra.go.app.AppGateState
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.RemoteCallingGitRepository
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.data.workspace.resolveAppGateState
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Proves post-setup local note flows do not invoke remote Git operations. */
class OfflineWorkspaceUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = "file:///tmp/example.git",
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun appGateReady_doesNotCallRemoteGit() {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/Offline.md").writeText("# Offline\n\nBody")

        val state = resolveAppGateState(config, filesDir)

        assertEquals(AppGateState.Ready(config), state)
    }

    @Test
    fun inboxReadEditSave_useLocalWorkspaceWithoutRemoteGit() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/Offline.md").writeText("# Offline\n\nOriginal")

        val gitRepository = RemoteCallingGitRepository()
        val registryCache = NoteRegistryCache(FileNoteRegistryRepository())
        val contentRepository = FileNoteContentRepository()
        val writeRepository = FileNoteWriteRepository(gitRepository)
        val loadGitStatusSummary = LoadGitStatusSummary(gitRepository)

        val inbox = LoadInboxSummaries(registryCache)
        val read = LoadNoteForReading(registryCache, contentRepository)
        val save = SaveNote(writeRepository, registryCache, loadGitStatusSummary)

        val noteId = NoteId("Inbox/Offline.md")
        assertTrue(inbox(config, filesDir).isSuccess)
        assertTrue(read(config, filesDir, noteId).isSuccess)
        assertTrue(save(config, filesDir, noteId, "# Offline\n\nEdited offline").isSuccess)

        assertEquals(0, gitRepository.cloneCallCount)
        assertEquals(0, gitRepository.fetchCallCount)
        assertEquals(0, gitRepository.pullCallCount)
        assertEquals(0, gitRepository.pushCallCount)
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
