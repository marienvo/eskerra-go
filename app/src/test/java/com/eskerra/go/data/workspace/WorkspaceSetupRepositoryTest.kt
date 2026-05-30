package com.eskerra.go.data.workspace

import com.eskerra.go.data.git.CredentialRecordingGitRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.StatusFailingGitRepository
import com.eskerra.go.data.git.TestGitRepos
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceSetupRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepository = JGitWorkspaceRepository()
    private val repository = DefaultWorkspaceSetupRepository(gitRepository)

    private fun filesDir(): File = temp.newFolder("files")

    @Test
    fun initializeLocal_createsGitRepoAtFixedPath() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Local Notes",
            branch = "",
            remoteUri = null,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("Local Notes", config.name)
        assertEquals(WorkspacePaths.DEFAULT_RELATIVE_PATH, config.relativePath)
        assertNull(config.remoteUri)
        assertTrue(config.branch.isNotBlank())

        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        assertTrue(WorkspacePaths.isValidGitWorkspace(workspaceDir))
    }

    @Test
    fun initializeLocal_buildConfigFailure_cleansUpWorkspaceDirectory() = runTest {
        val filesDir = filesDir()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        val repository = DefaultWorkspaceSetupRepository(StatusFailingGitRepository())

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Local Notes",
            branch = "",
            remoteUri = null,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.StorageFailed)
        assertFalse(workspaceDir.exists())
    }

    @Test
    fun cloneFromFileRemote_populatesFixedWorkspacePath() = runTest {
        val filesDir = filesDir()
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)

        val producer = temp.newFolder("producer")
        gitRepository.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepository.writeFile(producer, "notes/seed.md", "# Seed\n").getOrThrow()
        gitRepository.stageAll(producer).getOrThrow()
        gitRepository.commit(producer, "Seed").getOrThrow()
        gitRepository.push(producer).getOrThrow()
        val remoteBranch = gitRepository.status(producer).getOrThrow().branch

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Cloned Notes",
            branch = remoteBranch,
            remoteUri = remoteUri,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("Cloned Notes", config.name)
        assertEquals(remoteUri, config.remoteUri)
        assertEquals(remoteBranch, config.branch)
        assertTrue(File(filesDir, "workspace/notes/seed.md").isFile)
    }

    @Test
    fun clone_rejectsCredentialBearingHttpsUri() = runTest {
        val filesDir = filesDir()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = "https://mysecrettoken@example.com/repo.git",
            credential = "pat-12345",
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.CredentialBearingRemoteUri)
        assertFalse(error.error.message().contains("mysecrettoken"))
        assertFalse(workspaceDir.exists())
    }

    @Test
    fun clone_rejectsCredentialBearingSshUri() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = "ssh://git@example.com/repo.git",
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.CredentialBearingRemoteUri)
    }

    @Test
    fun clone_rejectsCredentialBearingFileUri() = runTest {
        val filesDir = filesDir()
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = "file://user:mysecrettoken@/tmp/repo.git",
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.CredentialBearingRemoteUri)
        assertFalse(error.error.message().contains("mysecrettoken"))
        assertFalse(workspaceDir.exists())
    }

    @Test
    fun clone_httpsRequiresToken() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = "https://example.com/repo.git",
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.MissingCredential)
    }

    @Test
    fun clone_rejectsUnsupportedScheme() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = "git@example.com:repo.git",
            credential = "token",
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.UnsupportedRemoteScheme)
    }

    @Test
    fun clone_requiresRemoteUri() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "main",
            remoteUri = null,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.BlankRemoteUri)
    }

    @Test
    fun clone_rejectsBlankBranch() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "   ",
            remoteUri = "file:///tmp/example.git",
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.BlankBranch)
    }

    @Test
    fun completeSetup_rejectsBlankName() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "   ",
            branch = "",
            remoteUri = null,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.BlankName)
    }

    @Test
    fun clone_missingBranch_cleansUpWorkspaceDirectory() = runTest {
        val filesDir = filesDir()
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)

        val producer = temp.newFolder("producer")
        gitRepository.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepository.writeFile(producer, "notes/seed.md", "# Seed\n").getOrThrow()
        gitRepository.stageAll(producer).getOrThrow()
        gitRepository.commit(producer, "Seed").getOrThrow()
        gitRepository.push(producer).getOrThrow()

        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Cloned Notes",
            branch = "missing-branch",
            remoteUri = remoteUri,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(
            "expected BranchNotFound but was ${error.error}",
            error.error is WorkspaceSetupError.BranchNotFound
        )
        assertFalse(workspaceDir.exists())
    }

    @Test
    fun clone_missingRemotePath_mapsToInvalidRepository() = runTest {
        val filesDir = filesDir()
        val missingUri = TestGitRepos.fileUri(File(temp.root, "does-not-exist.git"))

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = "master",
            remoteUri = missingUri,
            credential = null,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(
            "expected InvalidRepository but was ${error.error}",
            error.error is WorkspaceSetupError.InvalidRepository
        )
    }

    @Test
    fun clone_rejectsLeadingAndTrailingBranchWhitespace() = runTest {
        val filesDir = filesDir()

        val result = repository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "Remote Notes",
            branch = " main ",
            remoteUri = "https://example.com/repo.git",
            credential = "token",
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.InvalidBranch)
    }

    @Test
    fun clone_rejectsUnsafeBranchNames() = runTest {
        val filesDir = filesDir()
        val unsafeBranches = listOf(
            "feature..fix",
            "/main",
            "main.lock",
            "refs/heads/main",
            "main:origin/main"
        )

        unsafeBranches.forEach { branch ->
            val result = repository.completeSetup(
                mode = WorkspaceSetupMode.Clone,
                name = "Remote Notes",
                branch = branch,
                remoteUri = "https://example.com/repo.git",
                credential = "token",
                filesDir = filesDir
            )
            assertTrue("branch '$branch' should be rejected", result.isFailure)
            val error = result.exceptionOrNull() as WorkspaceSetupException
            assertTrue(error.error is WorkspaceSetupError.InvalidBranch)
        }
    }

    @Test
    fun clone_httpsPassesTokenThroughCredentialProviderNotUrl() = runTest {
        val filesDir = filesDir()
        val bare = TestGitRepos.initBareRemote(File(temp.root, "https-remote.git"))
        val localRemoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("producer")
        gitRepository.cloneFrom(localRemoteUri, producer).getOrThrow()
        gitRepository.writeFile(producer, "notes/seed.md", "# Seed\n").getOrThrow()
        gitRepository.stageAll(producer).getOrThrow()
        gitRepository.commit(producer, "Seed").getOrThrow()
        gitRepository.push(producer).getOrThrow()
        val remoteBranch = gitRepository.status(producer).getOrThrow().branch

        val recordingGit = CredentialRecordingGitRepository()
        recordingGit.simulatedLocalRemoteUri = localRemoteUri
        val httpsRepository = DefaultWorkspaceSetupRepository(recordingGit)
        val httpsUri = "https://example.com/org/repo.git"
        val token = "secret-https-token"

        val result = httpsRepository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "HTTPS Notes",
            branch = remoteBranch,
            remoteUri = httpsUri,
            credential = token,
            filesDir = filesDir
        )

        assertTrue(result.isSuccess)
        assertEquals(httpsUri, recordingGit.lastCloneRemoteUri)
        assertEquals(token, recordingGit.lastCloneHttpsToken)
        assertFalse(recordingGit.lastCloneRemoteUri!!.contains(token))
        val config = result.getOrThrow()
        assertEquals(httpsUri, config.remoteUri)
        assertFalse(config.remoteUri!!.contains(token))

        val gitConfig = File(filesDir, "workspace/.git/config")
        assertTrue(gitConfig.isFile)
        assertFalse(gitConfig.readText().contains(token))
    }

    @Test
    fun clone_authFailureDoesNotExposeToken() = runTest {
        val filesDir = filesDir()
        val recordingGit = CredentialRecordingGitRepository()
        recordingGit.cloneFailure = RuntimeException(
            "Authentication failed for https://example.com/repo.git"
        )
        val httpsRepository = DefaultWorkspaceSetupRepository(recordingGit)
        val token = "secret-auth-token"

        val result = httpsRepository.completeSetup(
            mode = WorkspaceSetupMode.Clone,
            name = "HTTPS Notes",
            branch = "main",
            remoteUri = "https://example.com/repo.git",
            credential = token,
            filesDir = filesDir
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.AuthenticationFailed)
        assertFalse(error.error.message().contains(token))
    }
}
