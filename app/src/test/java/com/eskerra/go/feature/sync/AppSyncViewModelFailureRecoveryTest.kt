package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun unexpectedFailure_exitsSyncingAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var attempts = 0
            val viewModel = createViewModel { _, _, _ ->
                attempts++
                if (attempts == 1) error("unexpected sync failure")
                Result.success(successResult())
            }

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Error)

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Success)
            assertEquals(2, attempts)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun blockedQueuedAutoSync_releasesSpinner() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel(remote = BlockedPreflightRemote()) { _, _, _ ->
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.syncNow()
            testScheduler.runCurrent()
            viewModel.requestAutoSync()
            testScheduler.runCurrent()
            assertTrue(viewModel.syncSpinnerVisible.value)

            finishSync.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Ready)
            assertEquals(false, viewModel.syncSpinnerVisible.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
        syncRunner: suspend (
            WorkspaceConfig,
            File,
            (com.eskerra.go.core.model.SyncProgressStep) -> Unit
        ) -> Result<SyncResult>
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
        return AppSyncViewModel(
            config = config,
            filesDir = filesDir,
            loadSyncStatus = status,
            refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
                remoteSyncRepository = remote,
                credentialStore = credentials,
                loadSyncStatus = status,
                dispatcher = Dispatchers.Main
            ),
            buildSyncPreflight = preflight,
            buildSafeSyncDiagnostic = BuildSafeSyncDiagnostic(
                preflight,
                store,
                Dispatchers.Main
            ),
            manualSyncNow = ManualSyncNow(
                remoteSyncRepository = remote,
                credentialStore = credentials,
                registryCache = NoteRegistryCache(FakeRegistryRepository()),
                loadSyncStatus = status,
                dispatcher = Dispatchers.Main
            ),
            recordLastSyncAttempt = RecordLastSyncAttempt(store),
            syncRunner = syncRunner
        )
    }

    private fun successResult() = SyncResult(
        status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Up to date."
        ),
        committed = false,
        commitId = null,
        pushed = false,
        pulled = false
    )

    private class BlockedPreflightRemote : RemoteSyncRepository by FakeRemoteSyncRepository() {
        override fun status(workingDir: File): Result<GitWorkspaceStatus> = Result.success(
            GitWorkspaceStatus(
                branch = "main",
                hasUncommittedChanges = true,
                changedPaths = setOf(".git/config")
            )
        )

        override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
            SyncChangePartition(emptySet(), emptySet(), changedPaths)
    }
}
