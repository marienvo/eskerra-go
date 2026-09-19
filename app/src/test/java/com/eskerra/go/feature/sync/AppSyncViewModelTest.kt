package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LastSyncStatusStore
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class AppSyncViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private val fetchCount = AtomicInteger(0)

    @Before
    fun setUpWorkspace() {
        filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }

    @Test
    fun refreshRemoteStatus_emitsReadyState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(ioDispatcher)

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SyncUiState.Ready)
            assertEquals(1, fetchCount.get())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshRemoteStatus_debouncesRepeatedCalls() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        try {
            val viewModel = createViewModel(ioDispatcher, clock = { now })

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()
            assertEquals(1, fetchCount.get())

            now = 5_000L
            viewModel.refreshRemoteStatus()
            advanceUntilIdle()
            assertEquals(1, fetchCount.get())

            now = 31_000L
            viewModel.refreshRemoteStatus()
            advanceUntilIdle()
            assertEquals(2, fetchCount.get())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNow_emitsSuccessState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var inboxRefreshed = false
            val syncStateRepo = FakeSyncStateRepository()
            lateinit var scheduler: FakeVaultSyncScheduler
            scheduler = FakeVaultSyncScheduler(syncStateRepo) {
                backgroundScope.launch {
                    val snapshot = syncStateRepo.getRecord().requestedGeneration
                    syncStateRepo.updateStatus(
                        DurableSyncStatus.Running(
                            step = SyncProgressStep.PushingLocalCommits.name,
                            startedAtEpochMs = System.currentTimeMillis()
                        )
                    )
                    syncStateRepo.markGenerationCompleted(
                        snapshot = snapshot,
                        completedAtEpochMs = System.currentTimeMillis()
                    )
                }
            }

            val viewModel = createViewModel(
                ioDispatcher = ioDispatcher,
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler,
                onSyncSuccess = { inboxRefreshed = true }
            )

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Success)
            assertTrue(inboxRefreshed)
            assertEquals(1, scheduler.scheduledCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNow_whileSyncing_incrementsGenerationAndSchedulesFollowUp() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)

            val viewModel = createViewModel(
                ioDispatcher = ioDispatcher,
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()

            viewModel.syncNow()
            testScheduler.runCurrent()
            viewModel.syncNow()
            advanceUntilIdle()

            assertEquals(2, scheduler.scheduledCount)
            assertEquals(2L, syncStateRepo.getRecord().requestedGeneration)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun durableRetrying_emitsErrorState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)

            val viewModel = createViewModel(
                ioDispatcher = ioDispatcher,
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            advanceUntilIdle()
            syncStateRepo.updateStatus(
                DurableSyncStatus.Retrying(
                    reason = "Remote unavailable",
                    nextAttemptAtEpochMs = System.currentTimeMillis() + 10_000L
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SyncUiState.Error)
            assertEquals("Remote unavailable", (state as SyncUiState.Error).message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNow_cancelsInFlightLoadJob_doesNotOverwriteSyncing() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)

            val viewModel = createViewModel(
                ioDispatcher = ioDispatcher,
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.refreshRemoteStatus(force = true)
            testScheduler.runCurrent()
            viewModel.syncNow()
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value is SyncUiState.Syncing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshLocalStatusQuietly_keepsReadyState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(ioDispatcher)

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is SyncUiState.Ready)

            viewModel.refreshLocalStatusQuietly()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun transitionToSynced_withUpdatedConfig_callsOnConfigUpdated() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var updatedConfigReceived: WorkspaceConfig? = null
            val syncStateRepo = FakeSyncStateRepository()
            val scheduler = FakeVaultSyncScheduler(syncStateRepo)

            val initialConfig = testConfig()
            val newConfig = initialConfig.copy(branch = "synced-branch")

            val viewModel = createViewModel(
                ioDispatcher = ioDispatcher,
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler,
                readConfig = { newConfig },
                onConfigUpdated = { updatedConfigReceived = it }
            )

            // Start in running state
            syncStateRepo.updateStatus(
                DurableSyncStatus.Running("Pushing", 1000L)
            )
            testScheduler.runCurrent()

            // Transition to Synced
            syncStateRepo.updateStatus(
                DurableSyncStatus.Synced(2000L)
            )
            advanceUntilIdle()

            assertEquals("synced-branch", updatedConfigReceived?.branch)
            assertTrue(viewModel.uiState.value is SyncUiState.Success)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        ioDispatcher: CoroutineDispatcher,
        syncStateRepository: SyncStateRepository = FakeSyncStateRepository(),
        vaultSyncScheduler: VaultSyncScheduler = FakeVaultSyncScheduler(syncStateRepository),
        readConfig: suspend () -> WorkspaceConfig? = { null },
        onSyncSuccess: () -> Unit = {},
        onConfigUpdated: (WorkspaceConfig) -> Unit = {},
        clock: () -> Long = { 0L },
        remoteSyncRepository: com.eskerra.go.core.repository.RemoteSyncRepository? = null,
        lastSyncStore: LastSyncStatusStore = FakeWorkspaceStore()
    ): AppSyncViewModel {
        val status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Up to date."
        )
        val fakeRemote = remoteSyncRepository ?: FetchTrackingFakeRemoteSyncRepository(
            inner = FakeRemoteSyncRepository(status),
            fetchCount = fetchCount
        )
        val credentials = FakeCredentialStore()
        val loadSyncStatus = LoadSyncStatus(fakeRemote, ioDispatcher)
        val refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
            remoteSyncRepository = fakeRemote,
            credentialStore = credentials,
            loadSyncStatus = loadSyncStatus,
            dispatcher = ioDispatcher
        )
        val buildPreflight = BuildSyncPreflight(fakeRemote, credentials, ioDispatcher)
        val buildDiagnostic = BuildSafeSyncDiagnostic(buildPreflight, lastSyncStore, ioDispatcher)
        return AppSyncViewModel(
            config = testConfig(),
            loadSyncStatus = { cfg -> loadSyncStatus(cfg, filesDir) },
            refreshRemoteSyncStatus = { cfg -> refreshRemoteSyncStatus(cfg, filesDir) },
            buildSyncPreflight = { cfg -> buildPreflight(cfg, filesDir) },
            buildSafeSyncDiagnostic = { cfg -> buildDiagnostic(cfg, filesDir) },
            syncStateRepository = syncStateRepository,
            vaultSyncScheduler = vaultSyncScheduler,
            readConfig = readConfig,
            onSyncSuccess = onSyncSuccess,
            onConfigUpdated = onConfigUpdated,
            refreshDebounceMs = 30_000L,
            clock = clock
        )
    }

    private fun testConfig(): WorkspaceConfig {
        val branch = JGitWorkspaceRepository().status(
            File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        ).getOrThrow().branch
        return WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "file:///tmp/remote.git",
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
    }

    private class FetchTrackingFakeRemoteSyncRepository(
        private val inner: FakeRemoteSyncRepository,
        private val fetchCount: AtomicInteger
    ) : com.eskerra.go.core.repository.RemoteSyncRepository by inner {

        override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> {
            fetchCount.incrementAndGet()
            return inner.fetch(workingDir, httpsToken)
        }
    }
}
