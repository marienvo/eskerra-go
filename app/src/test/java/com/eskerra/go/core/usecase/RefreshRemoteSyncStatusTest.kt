package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RefreshRemoteSyncStatusTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val fetchCount = AtomicInteger(0)
    private val remoteSync = FetchCountingRemoteSyncRepository(
        delegate = JGitRemoteSyncRepository(gitRepo),
        fetchCount = fetchCount
    )
    private val loadSyncStatus = LoadSyncStatus(remoteSync)
    private val refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
        remoteSyncRepository = remoteSync,
        credentialStore = FakeCredentialStore(),
        loadSyncStatus = loadSyncStatus
    )

    @Test
    fun returnsUnavailable_withoutCallingFetch_whenRemoteNotConfigured() = runTest {
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

        val status = refreshRemoteSyncStatus(config, filesDir)

        assertEquals(SyncStatusState.Unavailable, status.state)
        assertEquals(0, fetchCount.get())
    }

    @Test
    fun fetchesAndReturnsClean_whenRemoteIsUpToDate() = runTest {
        val setup = cloneSeededWorkspace()

        val status = refreshRemoteSyncStatus(setup.config, setup.filesDir)

        assertEquals(1, fetchCount.get())
        assertEquals(SyncStatusState.Clean, status.state)
    }

    @Test
    fun returnsError_whenFetchFails() = runTest {
        val setup = cloneSeededWorkspace()
        remoteSync.failFetch = true

        val status = refreshRemoteSyncStatus(setup.config, setup.filesDir)

        assertEquals(SyncStatusState.Error, status.state)
        assertEquals(RefreshRemoteSyncStatus.FETCH_FAILED_MESSAGE, status.message)
    }

    private data class WorkspaceSetup(val filesDir: File, val config: WorkspaceConfig)

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
        return WorkspaceSetup(filesDir, config)
    }

    private class FetchCountingRemoteSyncRepository(
        private val delegate: com.eskerra.go.core.repository.RemoteSyncRepository,
        private val fetchCount: AtomicInteger
    ) : com.eskerra.go.core.repository.RemoteSyncRepository by delegate {

        var failFetch: Boolean = false

        override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> {
            fetchCount.incrementAndGet()
            if (failFetch) {
                return Result.failure(IllegalStateException("fetch failed"))
            }
            return delegate.fetch(workingDir, httpsToken)
        }
    }
}
