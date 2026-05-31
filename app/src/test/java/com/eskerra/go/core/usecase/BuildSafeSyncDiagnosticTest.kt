package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.RemoteUriDisplay
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildSafeSyncDiagnosticTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()
    private val lastSyncStore = FakeWorkspaceStore()
    private val buildDiagnostic = BuildSafeSyncDiagnostic(
        buildSyncPreflight = BuildSyncPreflight(remoteSync, credentials),
        lastSyncStatusStore = lastSyncStore
    )

    @Test
    fun diagnostic_doesNotIncludeToken() = runTest {
        val token = "super-secret-token-value"
        val setup = cloneSeededWorkspace(
            remoteUri = "https://github.com/example/notes.git"
        )
        credentials.saveToken(setup.config.relativePath, token)

        val diagnostic = buildDiagnostic(setup.config, setup.filesDir)
        val rendered = listOfNotNull(
            diagnostic.sanitizedRemote,
            diagnostic.branch
        ).joinToString(" ")

        assertFalse(rendered.contains(token))
    }

    @Test
    fun diagnostic_credentialBearingUri_notShown() = runTest {
        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "https://token:secret@github.com/user/notes.git",
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        gitRepo.initOrOpen(File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)).getOrThrow()

        val diagnostic = buildDiagnostic(config, filesDir)

        assertNull(RemoteUriDisplay.sanitize(config.remoteUri))
        assertNull(diagnostic.sanitizedRemote)
    }

    private data class Setup(val config: WorkspaceConfig, val filesDir: File)

    private fun cloneSeededWorkspace(remoteUri: String? = null): Setup {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val defaultUri = TestGitRepos.fileUri(bare)
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(defaultUri, workspaceDir).getOrThrow()
        val branch = gitRepo.status(workspaceDir).getOrThrow().branch
        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri ?: defaultUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        return Setup(config, filesDir)
    }
}
