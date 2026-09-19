package com.eskerra.go.data.sync

import androidx.work.ListenableWorker.Result
import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.notes.CoalescingNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
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

class VaultSyncWorkerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()

    private fun createTestRuntime(
        filesDir: File,
        config: WorkspaceConfig? = null,
        initialRecord: DurableSyncRecord = DurableSyncRecord()
    ): Triple<SyncRuntime, FakeSyncStateRepository, FakeVaultSyncScheduler> {
        val workspaceStore = FakeWorkspaceStore().apply {
            if (config != null) {
                kotlinx.coroutines.runBlocking { save(config) }
            }
        }
        val credentials = FakeCredentialStore()
        val gitSyncMutex = GitSyncMutex()
        val syncStateRepository = FakeSyncStateRepository(initialRecord)
        val remoteSync = JGitRemoteSyncRepository(gitRepo)
        val loadSyncStatus = LoadSyncStatus(remoteSync)
        val refreshRemoteSyncStatus =
            RefreshRemoteSyncStatus(remoteSync, credentials, loadSyncStatus)
        val buildSyncPreflight = BuildSyncPreflight(remoteSync, credentials)
        val buildSafeSyncDiagnostic = BuildSafeSyncDiagnostic(buildSyncPreflight, workspaceStore)
        val recordLastSyncAttempt = RecordLastSyncAttempt(workspaceStore)

        val fileRegistry = FileNoteRegistryRepository()
        val coalescing = CoalescingNoteRegistryRepository(fileRegistry)
        val noteRegistryCache = NoteRegistryCache(coalescing)
        val noteContentCache = NoteContentCache(FileNoteContentRepository())
        val reconcileBranch = ReconcileWorkspaceSyncBranch(
            workspaceStore = workspaceStore,
            credentialStore = credentials,
            remoteSyncRepository = remoteSync,
            gitSyncMutex = gitSyncMutex
        )
        val manualSync = ManualSyncNow(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            registryCache = noteRegistryCache,
            contentCache = noteContentCache,
            loadSyncStatus = loadSyncStatus,
            reconcileWorkspaceSyncBranch = reconcileBranch,
            gitSyncMutex = gitSyncMutex
        )
        val scheduler = FakeVaultSyncScheduler()

        val runtime = SyncRuntime(
            context = null,
            filesDir = filesDir,
            workspaceStore = workspaceStore,
            bootCacheStore = workspaceStore,
            credentialStore = credentials,
            gitSyncMutex = gitSyncMutex,
            syncStateRepository = syncStateRepository,
            remoteSyncRepository = remoteSync,
            noteRegistryRepository = coalescing,
            noteRegistryCache = noteRegistryCache,
            noteContentCache = noteContentCache,
            loadSyncStatus = loadSyncStatus,
            refreshRemoteSyncStatus = refreshRemoteSyncStatus,
            buildSyncPreflight = buildSyncPreflight,
            buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
            recordLastSyncAttempt = recordLastSyncAttempt,
            reconcileWorkspaceSyncBranch = reconcileBranch,
            manualSyncNow = manualSync,
            vaultSyncScheduler = scheduler
        )
        return Triple(runtime, syncStateRepository, scheduler)
    }

    private fun cloneSeededWorkspace():
        Triple<SyncRuntime, FakeSyncStateRepository, FakeVaultSyncScheduler> {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)

        val producer = temp.newFolder("producer")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        gitRepo.writeFile(
            producer,
            "${InboxNotePath.INBOX_DIRECTORY}/seed.md",
            "# Seed\n"
        ).getOrThrow()
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        val branch = gitRepo.status(producer).getOrThrow().branch

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
        return createTestRuntime(
            filesDir = filesDir,
            config = config,
            initialRecord = DurableSyncRecord(requestedGeneration = 1L, completedGeneration = 0L)
        )
    }

    @Test
    fun noOpWhenNotPendingAndAlreadySynced() = runTest {
        val filesDir = temp.newFolder("files")
        val (runtime, _, _) = createTestRuntime(
            filesDir = filesDir,
            initialRecord = DurableSyncRecord(requestedGeneration = 1L, completedGeneration = 1L)
        )

        val result = VaultSyncWorker.performSync(runtime, setForegroundInfo = {})
        assertEquals(Result.success(), result)
    }

    @Test
    fun missingRemoteConfig_setsBlockedAndFails() = runTest {
        val filesDir = temp.newFolder("files")
        val (runtime, stateRepo, _) = createTestRuntime(
            filesDir = filesDir,
            config = WorkspaceConfig(
                name = "test",
                relativePath = "vault",
                remoteUri = null,
                branch = "main",
                setupCompletedAtEpochMs = 0L
            ),
            initialRecord = DurableSyncRecord(requestedGeneration = 1L, completedGeneration = 0L)
        )

        val result = VaultSyncWorker.performSync(runtime, setForegroundInfo = {})
        assertEquals(Result.failure(), result)

        val status = stateRepo.getRecord().status
        assertTrue(status is DurableSyncStatus.Blocked)
        assertEquals("Remote sync is not configured.", (status as DurableSyncStatus.Blocked).reason)
    }

    @Test
    fun successfulSync_advancesCompletedGenerationAndSetsSynced() = runTest {
        val (runtime, stateRepo, scheduler) = cloneSeededWorkspace()

        val result = VaultSyncWorker.performSync(runtime, setForegroundInfo = {})
        assertEquals(Result.success(), result)

        val record = stateRepo.getRecord()
        assertEquals(1L, record.completedGeneration)
        assertFalse(record.isPending)
        assertTrue(record.status is DurableSyncStatus.Synced)
        assertEquals(0, scheduler.scheduledCount)
    }

    @Test
    fun followUpScheduled_whenNewerGenerationArrivesDuringSync() = runTest {
        val (runtime, stateRepo, scheduler) = cloneSeededWorkspace()

        val result = VaultSyncWorker.performSync(
            syncRuntime = runtime,
            setForegroundInfo = {},
            syncRunner = { config, filesDir, onProgress ->
                // Simulate mutation arriving during sync execution
                stateRepo.markGenerationRequested() // requested becomes 2L
                runtime.manualSyncNow(config, filesDir, onProgress)
            }
        )
        assertEquals(Result.success(), result)

        val record = stateRepo.getRecord()
        assertEquals(1L, record.completedGeneration)
        assertEquals(2L, record.requestedGeneration)
        assertTrue(record.isPending)
        assertEquals(1, scheduler.scheduledCount)
    }

    @Test
    fun transientErrorClassification() {
        assertTrue(VaultSyncWorker.isTransient(SyncError.RemoteUnavailable))
        assertTrue(VaultSyncWorker.isTransient(SyncError.PushRejected))
        assertTrue(VaultSyncWorker.isTransient(SyncError.SyncAlreadyRunning))
        assertTrue(VaultSyncWorker.isTransient(SyncError.GitFailed("timeout")))

        assertFalse(VaultSyncWorker.isTransient(SyncError.AuthenticationFailed))
        assertFalse(VaultSyncWorker.isTransient(SyncError.MissingRemoteConfig))
        assertFalse(VaultSyncWorker.isTransient(SyncError.UnsafeLocalPath))
        assertFalse(VaultSyncWorker.isTransient(SyncError.ManualInterventionRequired))
    }

    @Test
    fun backoffCalculationIsExponential() {
        assertEquals(10_000L, VaultSyncWorker.calculateBackoffMs(0))
        assertEquals(20_000L, VaultSyncWorker.calculateBackoffMs(1))
        assertEquals(40_000L, VaultSyncWorker.calculateBackoffMs(2))
        assertEquals(640_000L, VaultSyncWorker.calculateBackoffMs(6))
        assertEquals(640_000L, VaultSyncWorker.calculateBackoffMs(10)) // capped at 2^6
    }

    @Test
    fun lateProgressUpdate_doesNotOverwriteSyncedStatus() = runTest {
        val (runtime, stateRepo, _) = cloneSeededWorkspace()

        var capturedProgressCallback: ((SyncProgressStep) -> Unit)? = null
        val result = VaultSyncWorker.performSync(
            syncRuntime = runtime,
            setForegroundInfo = {},
            syncRunner = { config, filesDir, onProgress ->
                capturedProgressCallback = onProgress
                runtime.manualSyncNow(config, filesDir, onProgress)
            }
        )
        assertEquals(Result.success(), result)
        assertTrue(stateRepo.getRecord().status is DurableSyncStatus.Synced)

        // Late progress update arrives after sync has already completed
        capturedProgressCallback?.invoke(SyncProgressStep.PushingLocalCommits)

        val record = stateRepo.getRecord()
        assertTrue(record.status is DurableSyncStatus.Synced)
    }

    @Test
    fun cancelledSync_resetsDurableStatusToPending() = runTest {
        val (runtime, stateRepo, _) = cloneSeededWorkspace()

        try {
            VaultSyncWorker.performSync(
                syncRuntime = runtime,
                setForegroundInfo = {},
                syncRunner = { _, _, _ ->
                    throw kotlinx.coroutines.CancellationException("Worker stopped by system")
                }
            )
            org.junit.Assert.fail("Expected CancellationException")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected
        }

        val record = stateRepo.getRecord()
        assertEquals(DurableSyncStatus.Pending, record.status)
    }
}
