package com.eskerra.go.app

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.LastSyncStatus
import com.eskerra.go.core.model.SyncAttemptOutcome
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LastSyncStatusStore
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
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.sync.SyncUiState
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
    fun requestsDuringSync_coalesceToExactlyOneFollowUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val firstSyncCanFinish = CompletableDeferred<Unit>()
            var syncCount = 0
            val viewModel = createViewModel { _, _, _ ->
                syncCount++
                if (syncCount == 1) {
                    firstSyncCanFinish.await()
                }
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()
            repeat(6) { viewModel.requestAutoSync() }
            testScheduler.runCurrent()
            assertEquals(1, syncCount)

            firstSyncCanFinish.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, syncCount)
            assertTrue(viewModel.uiState.value is SyncUiState.Success)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun blockedPreflight_doesNotSyncOrEmitError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var syncCount = 0
            val attempts = RecordingLastSyncStatusStore()
            val viewModel = createViewModel(
                remote = UnsafePathRemoteSyncRepository(),
                attempts = attempts
            ) { _, _, _ ->
                syncCount++
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(0, syncCount)
            assertTrue(viewModel.uiState.value is SyncUiState.Ready)
            assertTrue(attempts.saved.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncAlreadyRunning_retriesWithoutRecordingFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var syncCount = 0
            val attempts = RecordingLastSyncStatusStore()
            val viewModel = createViewModel(
                attempts = attempts,
                retryDelay = {}
            ) { _, _, _ ->
                syncCount++
                if (syncCount == 1) {
                    Result.failure(SyncException(SyncError.SyncAlreadyRunning))
                } else {
                    Result.success(successResult())
                }
            }

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(2, syncCount)
            assertEquals(listOf(SyncAttemptOutcome.Success), attempts.saved.map { it.outcome })
            assertTrue(viewModel.uiState.value is SyncUiState.Success)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun permanentContention_givesUpQuietly_insteadOfSpinningForever() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var syncCount = 0
            val attempts = RecordingLastSyncStatusStore()
            val viewModel = createViewModel(
                attempts = attempts,
                retryDelay = {}
            ) { _, _, _ ->
                // Another git channel holds the shared mutex and never releases it.
                syncCount++
                Result.failure(SyncException(SyncError.SyncAlreadyRunning))
            }

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(
                AppSyncViewModel.MAX_AUTO_SYNC_CONTENTION_RETRIES + 1,
                syncCount
            )
            // Giving up is not a failure, and must not leave the shell stuck on Syncing.
            assertTrue(attempts.saved.isEmpty())
            assertTrue(viewModel.uiState.value is SyncUiState.Ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun noRemoteConfigured_doesNotSync_butStillRefreshesLocalStatus() = runTest {
        // A local-only vault (WorkspaceSetupMode.InitializeLocal) has no remote to sync against, but
        // now that boot, foreground-resume, and every write site all route through requestAutoSync(),
        // a pure no-op here would leave uiState stuck on Loading forever — the Sync screen's Loading
        // branch has no retry affordance, so it would be permanently stuck for that setup mode.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var syncCount = 0
            val attempts = RecordingLastSyncStatusStore()
            val viewModel = createViewModel(
                config = config(remoteUri = null),
                attempts = attempts
            ) { _, _, _ ->
                syncCount++
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertEquals(0, syncCount)
            assertTrue(attempts.saved.isEmpty())
            val ready = viewModel.uiState.value as SyncUiState.Ready
            assertEquals(SyncStatusState.Unavailable, ready.status.state)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedAutoSync_emitsErrorAndRecordsAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val attempts = RecordingLastSyncStatusStore()
            val viewModel = createViewModel(attempts = attempts) { _, _, _ ->
                Result.failure(SyncException(SyncError.RemoteUnavailable))
            }

            viewModel.requestAutoSync()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Error)
            assertEquals(1, attempts.saved.size)
            assertEquals(SyncAttemptOutcome.Failed, attempts.saved.single().outcome)
            assertEquals("RemoteUnavailable", attempts.saved.single().errorCategory)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        config: WorkspaceConfig = config(),
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
        attempts: RecordingLastSyncStatusStore = RecordingLastSyncStatusStore(),
        retryDelay: suspend () -> Unit = {},
        syncRunner: suspend (
            WorkspaceConfig,
            File,
            (SyncProgressStep) -> Unit
        ) -> Result<SyncResult>
    ): AppSyncViewModel {
        val dispatcher = Dispatchers.Main
        val credentials = FakeCredentialStore()
        val loadSyncStatus = LoadSyncStatus(remote, dispatcher)
        val preflight = BuildSyncPreflight(remote, credentials, dispatcher)
        return AppSyncViewModel(
            config = config,
            filesDir = filesDir,
            loadSyncStatus = loadSyncStatus,
            refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
                remoteSyncRepository = remote,
                credentialStore = credentials,
                loadSyncStatus = loadSyncStatus,
                dispatcher = dispatcher
            ),
            buildSyncPreflight = preflight,
            buildSafeSyncDiagnostic = BuildSafeSyncDiagnostic(
                preflight,
                attempts,
                dispatcher
            ),
            manualSyncNow = ManualSyncNow(
                remoteSyncRepository = remote,
                credentialStore = credentials,
                registryCache = NoteRegistryCache(FakeRegistryRepository()),
                loadSyncStatus = loadSyncStatus,
                dispatcher = dispatcher
            ),
            recordLastSyncAttempt = RecordLastSyncAttempt(attempts),
            autoSyncRetryDelay = retryDelay,
            syncRunner = syncRunner
        )
    }

    private fun config(remoteUri: String? = "file:///tmp/remote.git") = WorkspaceConfig(
        name = "Test",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = remoteUri,
        branch = branch,
        setupCompletedAtEpochMs = 0L
    )

    private fun successResult() = SyncResult(
        status = cleanStatus(),
        committed = false,
        commitId = null,
        pushed = false,
        pulled = false
    )

    private fun cleanStatus() = SyncStatusSummary(
        state = SyncStatusState.Clean,
        branch = branch,
        changedCount = 0,
        aheadCount = 0,
        behindCount = 0,
        message = "Up to date."
    )

    private class RecordingLastSyncStatusStore : LastSyncStatusStore {
        val saved = mutableListOf<LastSyncStatus>()

        override suspend fun readLastSyncStatus(): LastSyncStatus? = saved.lastOrNull()

        override suspend fun saveLastSyncStatus(status: LastSyncStatus) {
            saved += status
        }
    }

    private class UnsafePathRemoteSyncRepository :
        RemoteSyncRepository by FakeRemoteSyncRepository() {

        override fun status(workingDir: File): Result<GitWorkspaceStatus> = Result.success(
            GitWorkspaceStatus(
                branch = "main",
                hasUncommittedChanges = true,
                changedPaths = setOf(".git/config")
            )
        )

        override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
            SyncChangePartition(
                inboxPaths = emptySet(),
                nonInboxPaths = emptySet(),
                unsafePaths = changedPaths
            )
    }
}
