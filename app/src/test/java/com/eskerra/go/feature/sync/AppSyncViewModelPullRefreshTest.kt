package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SyncProgressStep
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [AppSyncViewModel.pullRefreshing] must be true only while a manual pull-triggered sync is in
 * flight, so the home screen's own refresh indicator never appears for an automatic sync (boot,
 * foreground return, note write) — those show only the hamburger badge spinner instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncViewModelPullRefreshTest {

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
    fun syncFromPull_showsPullIndicatorUntilSyncEnds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.syncFromPull()
            testScheduler.runCurrent()
            assertTrue(viewModel.pullRefreshing.value)

            syncStateRepo.updateStatus(
                DurableSyncStatus.Running(
                    step = SyncProgressStep.PushingLocalCommits.name,
                    startedAtEpochMs = System.currentTimeMillis()
                )
            )
            testScheduler.runCurrent()
            assertTrue(viewModel.pullRefreshing.value)

            syncStateRepo.markGenerationCompleted(
                snapshot = 1L,
                completedAtEpochMs = System.currentTimeMillis()
            )
            advanceTimeBy(AppSyncViewModel.SYNC_SPINNER_HOLD_MS + 50L)
            advanceUntilIdle()

            assertFalse(viewModel.pullRefreshing.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun requestAutoSync_neverShowsPullIndicator() = runTest {
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

            syncStateRepo.updateStatus(
                DurableSyncStatus.Running(
                    step = SyncProgressStep.PushingLocalCommits.name,
                    startedAtEpochMs = System.currentTimeMillis()
                )
            )
            testScheduler.runCurrent()

            assertTrue(viewModel.syncSpinnerVisible.value)
            assertFalse(viewModel.pullRefreshing.value)

            syncStateRepo.markGenerationCompleted(
                snapshot = 1L,
                completedAtEpochMs = System.currentTimeMillis()
            )
            advanceTimeBy(AppSyncViewModel.SYNC_SPINNER_HOLD_MS + 50L)
            advanceUntilIdle()

            assertFalse(viewModel.pullRefreshing.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncFromPull_noRemoteConfigured_clearsPullIndicatorImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)
            val viewModel = createViewModel(
                config = config(remoteUri = null),
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.syncFromPull()
            advanceUntilIdle()

            assertFalse(viewModel.pullRefreshing.value)
            assertEquals(0, scheduler.scheduledCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        config: WorkspaceConfig = config(),
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
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
}
