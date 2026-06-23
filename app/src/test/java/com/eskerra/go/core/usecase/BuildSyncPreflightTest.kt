package com.eskerra.go.core.usecase

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildSyncPreflightTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()
    private val buildPreflight = BuildSyncPreflight(remoteSync, credentials)

    @Test
    fun cleanWorkspace_canSync() = runTest {
        val setup = cloneSeededWorkspace()

        val preflight = buildPreflight(setup.config, setup.filesDir)

        assertTrue(preflight.canSync)
        assertTrue(preflight.inboxChangeCount == 0)
    }

    @Test
    fun inboxOnlyChanges_canSync() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/edit.md", "edit").getOrThrow()

        val preflight = buildPreflight(setup.config, setup.filesDir)

        assertTrue(preflight.canSync)
        assertTrue(preflight.inboxChangeCount > 0)
    }

    @Test
    fun nonInboxWorkingTreeChanges_canSync() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Projects/edit.md", "edit").getOrThrow()

        val preflight = buildPreflight(setup.config, setup.filesDir)

        assertTrue(preflight.canSync)
        assertEquals(null, preflight.blockReason)
        assertTrue(preflight.nonInboxChangeCount > 0)
    }

    @Test
    fun missingToken_blocksSync() = runTest {
        val setup = cloneSeededWorkspace()
        val config = setup.config.copy(remoteUri = "https://github.com/example/notes.git")

        val preflight = buildPreflight(config, setup.filesDir)

        assertFalse(preflight.canSync)
        assertTrue(preflight.blockReason is SyncError.MissingCredential)
    }

    @Test
    fun stagedNonInboxFile_canSync() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Projects/staged.md", "staged").getOrThrow()
        Git.open(setup.workspaceDir).use { git ->
            git.add().addFilepattern("Projects/staged.md").call()
        }

        val preflight = buildPreflight(setup.config, setup.filesDir)

        assertTrue(preflight.canSync)
        assertEquals(null, preflight.blockReason)
    }

    @Test
    fun mergeInProgress_canSync() = runTest {
        val setup = cloneSeededWorkspace()
        File(setup.workspaceDir, ".git/MERGE_HEAD").writeText("deadbeef")

        val preflight = buildPreflight(setup.config, setup.filesDir)

        assertTrue(preflight.canSync)
        assertTrue(preflight.repoInterventionRequired)
        assertTrue(preflight.userMessage.contains("recover"))
    }

    private data class Setup(
        val config: WorkspaceConfig,
        val filesDir: File,
        val workspaceDir: File
    )

    private fun cloneSeededWorkspace(): Setup {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()
        val producer = temp.newFolder("seed")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "${InboxNotePath.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        val branch = gitRepo.status(workspaceDir).getOrThrow().branch
        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        return Setup(config, filesDir, workspaceDir)
    }
}
