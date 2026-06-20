package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncPodcastChangeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()

    private fun syncPodcastChange() = SyncPodcastChange(
        remoteSyncRepository = remoteSync,
        credentialStore = credentials,
        gitSyncMutex = GitSyncMutex()
    )

    @Test
    fun noPodcastChanges_returnsNoOpSuccess() = runTest {
        val setup = cloneSeededWorkspace()

        val result = syncPodcastChange()(setup.config, setup.filesDir).getOrThrow()

        assertFalse(result.committed)
        assertNull(result.commitId)
        assertFalse(result.pushed)
        assertFalse(result.pendingPush)
    }

    @Test
    fun podcastChange_commitsAndPushes() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; Title [▶](https://cdn/ep.mp3) (Daily)\n"
        ).getOrThrow()

        val result = syncPodcastChange()(setup.config, setup.filesDir).getOrThrow()

        assertTrue(result.committed)
        assertTrue(result.pushed)
        assertFalse(result.pendingPush)

        val verify = temp.newFolder("verify")
        gitRepo.cloneFrom(setup.remoteUri, verify).getOrThrow()
        assertTrue(File(verify, "General/2026 News - podcasts.md").isFile)
    }

    @Test
    fun podcastCommit_onlyStagesPodcastPaths() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; Title [▶](https://cdn/ep.mp3) (Daily)\n"
        ).getOrThrow()
        // An unrelated non-podcast change must be left untouched in the working tree.
        gitRepo.writeFile(setup.workspaceDir, "Projects/local.md", "edit").getOrThrow()

        syncPodcastChange()(setup.config, setup.filesDir).getOrThrow()

        val status = remoteSync.status(setup.workspaceDir).getOrThrow()
        assertTrue(status.changedPaths.contains("Projects/local.md"))
        assertFalse(status.changedPaths.contains("General/2026 News - podcasts.md"))
    }

    @Test
    fun noRemote_commitsLocallyWithoutPending() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; Title [▶](https://cdn/ep.mp3) (Daily)\n"
        ).getOrThrow()
        val config = setup.config.copy(remoteUri = null)

        val result = syncPodcastChange()(config, setup.filesDir).getOrThrow()

        assertTrue(result.committed)
        assertFalse(result.pushed)
        assertFalse(result.pendingPush)
    }

    @Test
    fun offlinePush_keepsLocalCommitPending() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; Title [▶](https://cdn/ep.mp3) (Daily)\n"
        ).getOrThrow()
        val offlineSync = SyncPodcastChange(
            remoteSyncRepository = FailingRemoteSyncRepository(
                fetchError = RuntimeException("unable to access remote: connection refused")
            ),
            credentialStore = credentials,
            gitSyncMutex = GitSyncMutex()
        )

        val result = offlineSync(setup.config, setup.filesDir).getOrThrow()

        assertTrue(result.committed)
        assertFalse(result.pushed)
        assertTrue(result.pendingPush)
    }

    @Test
    fun divergedRemote_keepsLocalCommitPending() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; Title [▶](https://cdn/ep.mp3) (Daily)\n"
        ).getOrThrow()
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(setup.remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Projects/remote.md", "remote").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "remote change").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        val result = syncPodcastChange()(setup.config, setup.filesDir).getOrThrow()

        assertTrue(result.committed)
        assertFalse(result.pushed)
        assertTrue(result.pendingPush)
    }

    @Test
    fun mergeInProgress_returnsManualIntervention() = runTest {
        val setup = cloneSeededWorkspace()
        File(setup.workspaceDir, ".git/MERGE_HEAD").writeText("deadbeef")

        val result = syncPodcastChange()(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error
                is SyncError.ManualInterventionRequired
        )
    }

    @Test
    fun missingWorkspace_returnsTypedError() = runTest {
        val filesDir = temp.newFolder("empty-files")
        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = null,
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )

        val result = syncPodcastChange()(config, filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.WorkspaceUnavailable
        )
    }

    @Test
    fun secondPodcastChange_pushesPreviouslyPendingCommit() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; First [▶](https://cdn/ep1.mp3) (Daily)\n"
        ).getOrThrow()
        val offlineSync = SyncPodcastChange(
            remoteSyncRepository = FailingRemoteSyncRepository(
                fetchError = RuntimeException("unable to access remote: connection refused")
            ),
            credentialStore = credentials,
            gitSyncMutex = GitSyncMutex()
        )
        assertTrue(offlineSync(setup.config, setup.filesDir).getOrThrow().pendingPush)

        gitRepo.writeFile(
            setup.workspaceDir,
            "General/2026 News - podcasts.md",
            "- [ ] 2026-03-15; First [▶](https://cdn/ep1.mp3) (Daily)\n" +
                "- [ ] 2026-03-16; Second [▶](https://cdn/ep2.mp3) (Daily)\n"
        ).getOrThrow()

        val result = syncPodcastChange()(setup.config, setup.filesDir).getOrThrow()

        assertTrue(result.committed)
        assertTrue(result.pushed)

        val verify = temp.newFolder("verify")
        gitRepo.cloneFrom(setup.remoteUri, verify).getOrThrow()
        val content = File(verify, "General/2026 News - podcasts.md").readText()
        assertTrue(content.contains("ep1.mp3"))
        assertTrue(content.contains("ep2.mp3"))
    }

    private data class WorkspaceSetup(
        val filesDir: File,
        val workspaceDir: File,
        val config: WorkspaceConfig,
        val remoteUri: String
    )

    private fun cloneSeededWorkspace(): WorkspaceSetup {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val branch = seedRemote(remoteUri)

        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()

        val config = WorkspaceConfig(
            name = "Test Workspace",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        return WorkspaceSetup(filesDir, workspaceDir, config, remoteUri)
    }

    private fun seedRemote(remoteUri: String): String {
        val producer = temp.newFolder("seed")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "General/seed.md", "# Seed\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return gitRepo.status(producer).getOrThrow().branch
    }
}
