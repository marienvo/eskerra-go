package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncViewModelFailureRecoveryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun failureState_exitsSyncingAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val syncStateRepo = FakeSyncStateRepository()
            var attempts = 0
            lateinit var scheduler: FakeVaultSyncScheduler
            scheduler = FakeVaultSyncScheduler(syncStateRepo) {
                backgroundScope.launch {
                    attempts++
                    if (attempts == 1) {
                        syncStateRepo.updateStatus(
                            DurableSyncStatus.Blocked(reason = "Git push failed")
                        )
                    } else {
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
            }

            val viewModel = createViewModel(
                syncStateRepository = syncStateRepo,
                vaultSyncScheduler = scheduler
            )

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Error)
            assertEquals("Git push failed", (viewModel.uiState.value as SyncUiState.Error).message)

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Success)
            assertEquals(2, attempts)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
        syncStateRepository: FakeSyncStateRepository = FakeSyncStateRepository(),
        vaultSyncScheduler: FakeVaultSyncScheduler = FakeVaultSyncScheduler(syncStateRepository)
    ): AppSyncViewModel {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        val git = JGitWorkspaceRepository()
        git.initOrOpen(workspaceDir).getOrThrow()
        val credentials = FakeCredentialStore()
        val store = FakeWorkspaceStore()
        val status = LoadSyncStatus(remote, Dispatchers.Main)
        val preflight = BuildSyncPreflight(remote, credentials, Dispatchers.Main)
        val config = WorkspaceConfig(
            name = "Test",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = "file:///tmp/remote.git",
            branch = git.status(workspaceDir).getOrThrow().branch,
            setupCompletedAtEpochMs = 0L
        )
        val refresh = RefreshRemoteSyncStatus(
            remoteSyncRepository = remote,
            credentialStore = credentials,
            loadSyncStatus = status,
            dispatcher = Dispatchers.Main
        )
        val diagnostic = BuildSafeSyncDiagnostic(
            preflight,
            store,
            Dispatchers.Main
        )
        return AppSyncViewModel(
            config = config,
            loadSyncStatus = { cfg -> status(cfg, filesDir) },
            refreshRemoteSyncStatus = { cfg -> refresh(cfg, filesDir) },
            buildSyncPreflight = { cfg -> preflight(cfg, filesDir) },
            buildSafeSyncDiagnostic = { cfg -> diagnostic(cfg, filesDir) },
            syncStateRepository = syncStateRepository,
            vaultSyncScheduler = vaultSyncScheduler
        )
    }
}
