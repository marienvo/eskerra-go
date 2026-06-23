package com.eskerra.go.core.usecase

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.RemoteSyncSettingsException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.DefaultRemoteSyncSettingsRepository
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestRemoteConnectionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val credentials = FakeCredentialStore()

    @Test
    fun testConnection_usesLsRemoteWithoutMutatingLocalRepo() = runTest {
        val recording = TestConnectionRecordingRemoteSyncRepository()
        val filesDir = temp.newFolder("files")
        val (remoteUri, branch) = preparedRemote()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()
        val gitConfigBefore = File(workspaceDir, ".git/config").readText()
        val refsBefore = File(workspaceDir, ".git/refs/remotes").listFiles()?.size ?: 0

        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        TestRemoteConnection(
            DefaultRemoteSyncSettingsRepository(
                workspaceStore = FakeWorkspaceStore(),
                credentialStore = credentials,
                remoteSyncRepository = recording
            )
        )(config, filesDir).getOrThrow()

        assertTrue(recording.probeCalled)
        assertFalse(recording.configureOriginCalled)
        assertFalse(recording.fetchCalled)
        assertFalse(recording.stageInboxCalled)
        assertFalse(recording.commitCalled)
        assertFalse(recording.pushCalled)
        assertFalse(recording.fastForwardCalled)
        assertEquals(gitConfigBefore, File(workspaceDir, ".git/config").readText())
        assertEquals(refsBefore, File(workspaceDir, ".git/refs/remotes").listFiles()?.size ?: 0)
    }

    @Test
    fun testConnection_authFailure_mapsToSafeError() = runTest {
        val recording = TestConnectionRecordingRemoteSyncRepository(
            delegate = FailingRemoteSyncRepository(
                probeError = RuntimeException("authentication failed for user")
            )
        )
        val filesDir = temp.newFolder("files")
        val (remoteUri, branch) = preparedRemote()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()
        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "https://github.com/example/notes.git",
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        credentials.saveToken(config.relativePath, "secret-token").getOrThrow()

        val result = TestRemoteConnection(
            DefaultRemoteSyncSettingsRepository(
                workspaceStore = FakeWorkspaceStore(),
                credentialStore = credentials,
                remoteSyncRepository = recording
            )
        )(config, filesDir)

        assertTrue(result.isFailure)
        val message = (result.exceptionOrNull() as RemoteSyncSettingsException).error.message()
        assertTrue(message.contains("Authentication failed"))
        assertFalse(message.contains("secret-token"))
        assertFalse(recording.configureOriginCalled)
        assertFalse(recording.fetchCalled)
    }

    private fun preparedRemote(): Pair<String, String> {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "${InboxNotePath.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return remoteUri to gitRepo.status(producer).getOrThrow().branch
    }
}
