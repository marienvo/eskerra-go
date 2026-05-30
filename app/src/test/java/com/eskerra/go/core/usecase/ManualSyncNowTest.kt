package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.MarkdownNoteScanner
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManualSyncNowTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()
    private val registry = FileNoteRegistryRepository()
    private val loadSyncStatus = LoadSyncStatus(remoteSync)
    private val manualSync = ManualSyncNow(
        remoteSyncRepository = remoteSync,
        credentialStore = credentials,
        registryRepository = registry,
        loadSyncStatus = loadSyncStatus
    )

    @Test
    fun cleanWorkspaceSync_isNoOpSuccess() = runTest {
        val setup = cloneSeededWorkspace()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
        val syncResult = result.getOrThrow()
        assertFalse(syncResult.committed)
        assertFalse(syncResult.pushed)
        assertFalse(syncResult.pulled)
    }

    @Test
    fun localInboxEdit_createsCommit() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/new.md", "# New\n").getOrThrow()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().committed)
        val message = lastCommitMessage(setup.workspaceDir)
        assertTrue(message?.contains(ManualSyncNow.INBOX_COMMIT_MESSAGE) == true)
    }

    @Test
    fun localInboxCommit_pushesToRemote() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/push.md", "# Push\n").getOrThrow()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().pushed)

        val verify = temp.newFolder("verify")
        gitRepo.cloneFrom(setup.remoteUri, verify).getOrThrow()
        assertTrue(File(verify, "Inbox/push.md").isFile)
    }

    @Test
    fun remoteChanges_fastForwardIntoLocalWorkspace() = runTest {
        val setup = cloneSeededWorkspace()
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(setup.remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Projects/updated.md", "# Updated\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Remote update").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().pulled)
        assertTrue(File(setup.workspaceDir, "Projects/updated.md").isFile)
    }

    @Test
    fun remoteChangesOutsideInbox_becomeReadableAfterSync() = runTest {
        val setup = cloneSeededWorkspace()
        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(setup.remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Projects/remote-note.md", "# Remote\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Remote note").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        manualSync(setup.config, setup.filesDir).getOrThrow()
        val refreshed = registry.refresh(setup.config, setup.filesDir).getOrThrow()
        val note = refreshed.notes.find { it.id.value == "Projects/remote-note.md" }
        assertTrue(note != null)
        assertFalse(note!!.isInbox)
    }

    @Test
    fun divergedHistory_stopsWithTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/local.md", "local").getOrThrow()
        gitRepo.stageAll(setup.workspaceDir).getOrThrow()
        gitRepo.commit(setup.workspaceDir, "local divergent").getOrThrow()

        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(setup.remoteUri, producer).getOrThrow()
        gitRepo.writeFile(producer, "Projects/remote.md", "remote").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "remote divergent").getOrThrow()
        gitRepo.push(producer).getOrThrow()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as SyncException).error
        assertTrue(error is SyncError.Diverged)
    }

    @Test
    fun syncRefuses_localNonInboxChanges() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Projects/local-edit.md", "edit").getOrThrow()

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.NonInboxLocalChanges
        )
    }

    @Test
    fun syncRefuses_unsafeLocalPaths() = runTest {
        val setup = cloneSeededWorkspace()
        val unsafeSync = ManualSyncNow(
            remoteSyncRepository = UnsafePathRemoteSyncRepository(),
            credentialStore = credentials,
            registryRepository = registry,
            loadSyncStatus = loadSyncStatus
        )

        val result = unsafeSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.UnsafeLocalPath
        )
    }

    @Test
    fun missingRemoteConfig_returnsTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        val config = setup.config.copy(remoteUri = null)

        val result = manualSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.MissingRemoteConfig
        )
    }

    @Test
    fun credentialBearingRemoteUri_returnsTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        val config = setup.config.copy(remoteUri = "https://token:secret@github.com/user/repo.git")
        credentials.saveToken(config.relativePath, "secret-token")

        val result = manualSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.InvalidRemoteUri
        )
    }

    @Test
    fun httpsMissingCredential_returnsTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        val config = setup.config.copy(
            remoteUri = "https://github.com/example/notes.git"
        )

        val result = manualSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.MissingCredential
        )
    }

    @Test
    fun unsupportedRemoteScheme_returnsTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        val config = setup.config.copy(remoteUri = "ssh://github.com/example/notes.git")

        val result = manualSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.UnsupportedRemoteScheme
        )
    }

    @Test
    fun missingRemoteBranch_stopsWithTypedError() = runTest {
        val setup = cloneSeededWorkspace()
        org.eclipse.jgit.api.Git.open(setup.workspaceDir).use { git ->
            git.checkout()
                .setCreateBranch(true)
                .setName("missing-branch")
                .setStartPoint("HEAD")
                .call()
        }
        val config = setup.config.copy(branch = "missing-branch")

        val result = manualSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as SyncException).error
        assertTrue(error is SyncError.RemoteBranchNotFound)
        assertEquals("missing-branch", (error as SyncError.RemoteBranchNotFound).branch)
    }

    @Test
    fun localInboxCommit_pushReturnsCleanStatus() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/push.md", "# Push\n").getOrThrow()

        val result = manualSync(setup.config, setup.filesDir).getOrThrow()

        assertTrue(result.pushed)
        assertEquals(SyncStatusState.Clean, result.status.state)
        assertEquals(0, result.status.aheadCount)
    }

    @Test
    fun pushRejection_mapsToSafeError() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/push.md", "# Push\n").getOrThrow()
        val failingSync = ManualSyncNow(
            remoteSyncRepository = FailingRemoteSyncRepository(
                pushError = RuntimeException("push rejected for refs/heads/main: REJECTED")
            ),
            credentialStore = credentials,
            registryRepository = registry,
            loadSyncStatus = loadSyncStatus
        )

        val result = failingSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error is SyncError.PushRejected
        )
    }

    @Test
    fun authFailure_mapsToSafeErrorWithoutToken() = runTest {
        val setup = cloneSeededWorkspace()
        val token = "super-secret-token-12345"
        credentials.saveToken(setup.config.relativePath, token)
        val failingSync = ManualSyncNow(
            remoteSyncRepository = FailingRemoteSyncRepository(
                fetchError = RuntimeException("authentication failed for user")
            ),
            credentialStore = credentials,
            registryRepository = registry,
            loadSyncStatus = loadSyncStatus
        )
        val config = setup.config.copy(remoteUri = "https://github.com/example/notes.git")

        val result = failingSync(config, setup.filesDir)

        assertTrue(result.isFailure)
        val message = (result.exceptionOrNull() as SyncException).error.message()
        assertTrue(message.contains("Authentication failed"))
        assertFalse(message.contains(token))
    }

    @Test
    fun httpsToken_passedThroughCredentialProvider_notGitConfig() = runTest {
        val setup = cloneSeededWorkspace()
        val token = "sync-test-token-value"
        credentials.saveToken(setup.config.relativePath, token)
        val recording = TokenRecordingRemoteSyncRepository()
        val httpsSync = ManualSyncNow(
            remoteSyncRepository = recording,
            credentialStore = credentials,
            registryRepository = registry,
            loadSyncStatus = LoadSyncStatus(recording)
        )
        val config = setup.config.copy(remoteUri = "https://github.com/example/notes.git")

        httpsSync(config, setup.filesDir).getOrThrow()

        assertEquals(token, recording.lastFetchToken)
        assertFalse(File(setup.workspaceDir, ".git/config").readText().contains(token))
    }

    @Test
    fun syncSuccess_refreshesNoteRegistry() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/synced.md", "# Synced\n").getOrThrow()

        manualSync(setup.config, setup.filesDir).getOrThrow()
        val refreshed = registry.refresh(setup.config, setup.filesDir).getOrThrow()

        assertTrue(refreshed.notes.any { it.id.value == "Inbox/synced.md" })
    }

    @Test
    fun progressSteps_emitDuringSync() = runTest {
        val setup = cloneSeededWorkspace()
        val steps = mutableListOf<com.eskerra.go.core.model.SyncProgressStep>()

        manualSync(setup.config, setup.filesDir) { steps += it }

        assertTrue(steps.contains(com.eskerra.go.core.model.SyncProgressStep.ValidatingWorkspace))
        assertTrue(steps.contains(com.eskerra.go.core.model.SyncProgressStep.Complete))
    }

    @Test
    fun syncRefuses_preStagedNonInboxFile() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Projects/staged.md", "staged").getOrThrow()
        org.eclipse.jgit.api.Git.open(setup.workspaceDir).use { git ->
            git.add().addFilepattern("Projects/staged.md").call()
        }

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as SyncException).error
        assertTrue(
            error is SyncError.NonInboxStagedChanges || error is SyncError.NonInboxLocalChanges
        )
    }

    @Test
    fun syncAllows_preStagedInboxFile() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/staged-only.md", "staged").getOrThrow()
        org.eclipse.jgit.api.Git.open(setup.workspaceDir).use { git ->
            git.add().addFilepattern("Inbox/staged-only.md").call()
        }

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
    }

    @Test
    fun syncCommit_containsOnlyInboxPaths() = runTest {
        val setup = cloneSeededWorkspace()
        gitRepo.writeFile(setup.workspaceDir, "Inbox/only-inbox.md", "# Only inbox\n").getOrThrow()

        manualSync(setup.config, setup.filesDir).getOrThrow()

        org.eclipse.jgit.api.Git.open(setup.workspaceDir).use { git ->
            val commit = git.log().call()
                .first { it.fullMessage.contains(ManualSyncNow.INBOX_COMMIT_MESSAGE) }
            val parent = commit.parents.firstOrNull()
                ?: error("Expected sync commit to have a parent")
            val reader = git.repository.newObjectReader()
            val oldTree = org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                reset(reader, parent.tree)
            }
            val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                reset(reader, commit.tree)
            }
            val diffs = git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call()
            val changedPaths = diffs.mapNotNull { entry ->
                when (entry.changeType) {
                    org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ->
                        entry.oldPath.takeIf { it != "/dev/null" }
                    else -> entry.newPath.takeIf { it != "/dev/null" }
                }
            }
            assertTrue(changedPaths.isNotEmpty())
            assertTrue(changedPaths.all { it == "Inbox" || it.startsWith("Inbox/") })
        }
    }

    @Test
    fun mergeInProgress_blocksSync() = runTest {
        val setup = cloneSeededWorkspace()
        File(setup.workspaceDir, ".git/MERGE_HEAD").writeText("deadbeef")

        val result = manualSync(setup.config, setup.filesDir)

        assertTrue(result.isFailure)
        assertTrue(
            (result.exceptionOrNull() as SyncException).error
                is SyncError.ManualInterventionRequired
        )
    }

    @Test
    fun concurrentSync_secondReturnsAlreadyRunning() = runTest {
        val setup = cloneSeededWorkspace()
        val slowRegistry = com.eskerra.go.data.notes.FakeNoteRegistryRepository()
        slowRegistry.setRefreshDelayMs(10_000)
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val slowSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryRepository = slowRegistry,
            loadSyncStatus = loadSyncStatus,
            dispatcher = testDispatcher
        )
        val first = launch(testDispatcher) { slowSync(setup.config, setup.filesDir) }
        testScheduler.runCurrent()
        val second = async(testDispatcher) { slowSync(setup.config, setup.filesDir) }
        testScheduler.runCurrent()

        assertTrue(second.isCompleted)
        val secondResult = second.getCompleted()
        assertTrue(secondResult.isFailure)
        assertTrue(
            (secondResult.exceptionOrNull() as SyncException).error is SyncError.SyncAlreadyRunning
        )
        first.cancel()
    }

    @Test
    fun registryRefreshFailure_returnsPartialSuccess() = runTest {
        val setup = cloneSeededWorkspace()
        val failingRegistry = FailingNoteRegistryRepository(registry)
        val partialSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryRepository = failingRegistry,
            loadSyncStatus = loadSyncStatus
        )

        val result = partialSync(setup.config, setup.filesDir)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().registryRefreshed)
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
        gitRepo.writeFile(producer, "${MarkdownNoteScanner.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.writeFile(producer, "Projects/read.md", "# Read\n").getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        return gitRepo.status(producer).getOrThrow().branch
    }

    private fun lastCommitMessage(workspaceDir: File): String? {
        val headFile = File(workspaceDir, ".git/logs/HEAD")
        if (!headFile.isFile) return null
        return headFile.readLines().lastOrNull()?.substringAfter('\t')
    }
}
