package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.sync.FakeSyncStateRepository
import com.eskerra.go.data.sync.FakeVaultSyncScheduler
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncViewModelTestAutoSync {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var branch: String

    @Before
    fun setUpWorkspace() {
        filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
        branch = JGitWorkspaceRepository().status(workspaceDir).getOrThrow().branch
    }

    @Test
    fun requestsDuringSync_incrementGenerationsAndSchedule() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.requestAutoSync()
            testScheduler.runCurrent()
            repeat(5) { viewModel.requestAutoSync() }
            advanceUntilIdle()

            val record = syncStateRepo.getRecord()
            assertEquals(6L, record.requestedGeneration)
            assertEquals(6, scheduler.scheduledCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun blockedPreflight_doesNotSyncOrEmitError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                remote = UnsafePathRemoteSyncRepository(),
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(0, scheduler.scheduledCount)
            assertEquals(0L, syncStateRepo.getRecord().requestedGeneration)
            assertTrue(viewModel.uiState.value is SyncUiState.Ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun noRemoteConfigured_doesNotSync_butStillRefreshesLocalStatus() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                config = config(remoteUri = null),
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(0, scheduler.scheduledCount)
            val ready = viewModel.uiState.value as SyncUiState.Ready
            assertEquals(SyncStatusState.Unavailable, ready.status.state)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun orphanedRunningState_reconcilesToPendingAndSchedules() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            syncStateRepo.updateStatus(
                DurableSyncStatus.Running(
                    step = "PushingLocalCommits",
                    startedAtEpochMs = System.currentTimeMillis() - 60_000L
                )
            )
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.reconcileOnBoot()
            advanceUntilIdle()

            assertEquals(1, scheduler.reconcileCount)
            assertEquals(1, scheduler.scheduledCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
        config: WorkspaceConfig = config(),
        syncStateRepository: SyncStateRepository = FakeSyncStateRepository(),
        vaultSyncScheduler: VaultSyncScheduler = FakeVaultSyncScheduler(syncStateRepository)
    ): AppSyncViewModel {
        val credentials = FakeCredentialStore()
        val loadSyncStatus = LoadSyncStatus(remote, Dispatchers.Unconfined)
        val refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
            remoteSyncRepository = remote,
            credentialStore = credentials,
            loadSyncStatus = loadSyncStatus,
            dispatcher = Dispatchers.Unconfined
        )
        val buildPreflight = BuildSyncPreflight(remote, credentials, Dispatchers.Unconfined)
        val buildDiagnostic = BuildSafeSyncDiagnostic(
            buildPreflight,
            FakeWorkspaceStore(),
            Dispatchers.Unconfined
        )
        return AppSyncViewModel(
            config = config,
            loadSyncStatus = { cfg -> loadSyncStatus(cfg, filesDir) },
            refreshRemoteSyncStatus = { cfg -> refreshRemoteSyncStatus(cfg, filesDir) },
            buildSyncPreflight = { cfg -> buildPreflight(cfg, filesDir) },
            buildSafeSyncDiagnostic = { cfg -> buildDiagnostic(cfg, filesDir) },
            syncStateRepository = syncStateRepository,
            vaultSyncScheduler = vaultSyncScheduler
        )
    }

    private fun config(remoteUri: String? = "file:///remote"): WorkspaceConfig = WorkspaceConfig(
        name = "Test",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = remoteUri,
        branch = branch,
        setupCompletedAtEpochMs = 0L
    )

    private class UnsafePathRemoteSyncRepository :
        RemoteSyncRepository by FakeRemoteSyncRepository() {
        override fun status(workingDir: File): Result<GitWorkspaceStatus> = Result.success(
            GitWorkspaceStatus(
                branch = "main",
                hasUncommittedChanges = true,
                changedPaths = setOf("..${File.separator}escape.md")
            )
        )

        override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
            SyncChangePartition(emptySet(), emptySet(), changedPaths)
    }
}
