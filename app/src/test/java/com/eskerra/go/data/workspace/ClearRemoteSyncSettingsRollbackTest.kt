package com.eskerra.go.data.workspace

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClearRemoteSyncSettingsRollbackTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()

    @Test
    fun clearSettings_gitFailure_preservesMetadataAndOrigin() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val remote = preparedRemote()
        val config = localConfig(filesDir).copy(
            remoteUri = remote.remoteUri,
            branch = remote.branch
        )
        val workspaceDir = File(filesDir, config.relativePath)
        workspaceStore.save(config)
        credentials.saveToken(config.relativePath, "token").getOrThrow()
        DefaultRemoteSyncSettingsRepository(
            workspaceStore = workspaceStore,
            credentialStore = credentials,
            remoteSyncRepository = remoteSync
        ).saveSettings(
            config = config,
            remoteUri = remote.remoteUri,
            branch = remote.branch,
            replacementToken = "token",
            filesDir = filesDir
        ).getOrThrow()

        val result = DefaultRemoteSyncSettingsRepository(
            workspaceStore = workspaceStore,
            credentialStore = credentials,
            remoteSyncRepository = ClearOriginFailingRemoteSyncRepository(remoteSync)
        ).clearSettings(config, filesDir)

        assertTrue(result.isFailure)
        assertEquals(remote.remoteUri, workspaceStore.read()?.remoteUri)
        assertEquals("token", credentials.readToken(config.relativePath).getOrThrow())
        assertTrue(File(workspaceDir, ".git/config").readText().contains("[remote \"origin\"]"))
    }

    private fun localConfig(filesDir: File): WorkspaceConfig {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        gitRepo.initOrOpen(workspaceDir).getOrThrow()
        return WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = null,
            branch = gitRepo.status(workspaceDir).getOrThrow().branch,
            setupCompletedAtEpochMs = 0L
        )
    }

    private fun preparedRemote(): PreparedRemote {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote-${System.nanoTime()}.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "${InboxNotePath.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return PreparedRemote(
            remoteUri = remoteUri,
            branch = gitRepo.status(producer).getOrThrow().branch
        )
    }

    private data class PreparedRemote(val remoteUri: String, val branch: String)

    private class ClearOriginFailingRemoteSyncRepository(
        private val delegate: RemoteSyncRepository
    ) : RemoteSyncRepository by delegate {

        override fun clearSanitizedOrigin(workingDir: File): Result<Unit> =
            Result.failure(RuntimeException("clear origin failed"))
    }
}
