package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadSyncStatusTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val loadSyncStatus = LoadSyncStatus(remoteSync)

    @Test
    fun returnsUnavailable_whenWorkspaceMissing() = runTest {
        val config = WorkspaceConfig(
            name = "Missing",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "file:///tmp/remote.git",
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )

        val status = loadSyncStatus(config, temp.newFolder("files"))

        assertEquals(SyncStatusState.Unavailable, status.state)
    }

    @Test
    fun returnsMissingRemoteMessage_whenRemoteNotConfigured() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        gitRepo.initOrOpen(workspaceDir).getOrThrow()
        val branch = gitRepo.status(workspaceDir).getOrThrow().branch

        val config = WorkspaceConfig(
            name = "Local",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = null,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )

        val status = loadSyncStatus(config, filesDir)

        assertEquals(SyncStatusState.Unavailable, status.state)
        assertEquals(SyncError.MissingRemoteConfig.message(), status.message)
    }

    @Test
    fun returnsClean_whenClonedAndUpToDate() = runTest {
        val setup = cloneSeededWorkspace()

        val status = loadSyncStatus(setup.config, setup.filesDir)

        assertEquals(SyncStatusState.Clean, status.state)
    }

    @Test
    fun returnsDirty_whenLocalChangesExist() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/draft.md", "draft").getOrThrow()

        val status = loadSyncStatus(setup.config, setup.filesDir)

        assertEquals(SyncStatusState.DirtyLocalChanges, status.state)
    }

    private data class WorkspaceSetup(
        val filesDir: File,
        val workspaceDir: File,
        val config: WorkspaceConfig
    )

    private fun cloneSeededWorkspace(): WorkspaceSetup {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Inbox/seed.md", "# Seed\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        val branch = gitRepo.status(producer).getOrThrow().branch

        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()

        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        return WorkspaceSetup(filesDir, workspaceDir, config)
    }
}
