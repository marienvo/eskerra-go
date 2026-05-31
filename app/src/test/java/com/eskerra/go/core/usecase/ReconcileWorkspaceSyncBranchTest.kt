package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.notes.MarkdownNoteScanner
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReconcileWorkspaceSyncBranchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val store = FakeWorkspaceStore()

    @Test
    fun invoke_masterConfigWithMainOnlyRemote_persistsMain() = runTest {
        val filesDir = temp.newFolder("files")
        val remote = preparedRemote()
        if (remote.branch != "main") return@runTest

        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        org.eclipse.jgit.api.Git.init()
            .setDirectory(workspaceDir)
            .setInitialBranch("master")
            .call()
            .close()
        remoteSync.configureSanitizedOrigin(workspaceDir, remote.remoteUri).getOrThrow()

        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remote.remoteUri,
            branch = "master",
            setupCompletedAtEpochMs = 0L
        )
        store.save(config)

        val result = ReconcileWorkspaceSyncBranch(
            workspaceStore = store,
            credentialStore = FakeCredentialStore(),
            remoteSyncRepository = remoteSync
        )(config, filesDir)

        assertTrue(result.isSuccess)
        assertEquals("main", result.getOrThrow().branch)
        assertEquals("main", store.read()?.branch)
        assertEquals("main", gitRepo.status(workspaceDir).getOrThrow().branch)
    }

    @Test
    fun invoke_httpsRemoteWithoutStoredToken_returnsMissingCredential() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        gitRepo.initOrOpen(workspaceDir).getOrThrow()

        val config = WorkspaceConfig(
            name = "Notes",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "https://github.com/example/notes.git",
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )

        val result = ReconcileWorkspaceSyncBranch(
            workspaceStore = store,
            credentialStore = FakeCredentialStore(),
            remoteSyncRepository = remoteSync
        )(config, filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.MissingCredential
        )
    }

    private data class PreparedRemote(val remoteUri: String, val branch: String)

    private fun preparedRemote(): PreparedRemote {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote-${System.nanoTime()}.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "${MarkdownNoteScanner.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return PreparedRemote(
            remoteUri = remoteUri,
            branch = gitRepo.status(producer).getOrThrow().branch
        )
    }
}
