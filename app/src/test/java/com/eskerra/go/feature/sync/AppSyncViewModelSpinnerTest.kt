package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.RemoteBranchComparison
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncProgressStep
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncViewModelSpinnerTest {

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
    fun manualSync_showsShellSpinnerWhileFetchingRemote() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel { _, _, onProgress ->
                onProgress(SyncProgressStep.FetchingRemote)
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.syncNow()
            testScheduler.runCurrent()

            assertTrue(viewModel.syncSpinnerVisible.value)
            finishSync.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.syncSpinnerVisible.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun automaticCleanFetch_keepsShellSpinnerHidden() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel { _, _, onProgress ->
                onProgress(SyncProgressStep.FetchingRemote)
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()

            assertFalse(viewModel.syncSpinnerVisible.value)
            finishSync.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.syncSpinnerVisible.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun automaticSync_showsShellSpinnerImmediatelyForKnownLocalChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel(
                remote = LocalChangesRemoteSyncRepository()
            ) { _, _, _ ->
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()

            assertTrue(viewModel.syncSpinnerVisible.value)
            finishSync.complete(Unit)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun automaticSync_showsShellSpinnerImmediatelyForKnownRemoteChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel(remote = RemoteBehindSyncRepository()) { _, _, _ ->
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()

            assertTrue(viewModel.syncSpinnerVisible.value)
            finishSync.complete(Unit)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun automaticSync_showsShellSpinnerWhenFetchFindsRemoteChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val beforeIntegrating = CompletableDeferred<Unit>()
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel { _, _, onProgress ->
                onProgress(SyncProgressStep.FetchingRemote)
                beforeIntegrating.await()
                onProgress(SyncProgressStep.IntegratingRemote)
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()
            assertFalse(viewModel.syncSpinnerVisible.value)

            beforeIntegrating.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(viewModel.syncSpinnerVisible.value)

            finishSync.complete(Unit)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun automaticSync_showsShellSpinnerWhenItStartsCommittingLocalChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val beforeCommit = CompletableDeferred<Unit>()
            val finishSync = CompletableDeferred<Unit>()
            val viewModel = createViewModel { _, _, onProgress ->
                onProgress(SyncProgressStep.FetchingRemote)
                beforeCommit.await()
                onProgress(SyncProgressStep.CommittingInboxChanges)
                finishSync.await()
                Result.success(successResult())
            }

            viewModel.requestAutoSync()
            testScheduler.runCurrent()
            assertFalse(viewModel.syncSpinnerVisible.value)

            beforeCommit.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(viewModel.syncSpinnerVisible.value)

            finishSync.complete(Unit)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        remote: RemoteSyncRepository = FakeRemoteSyncRepository(),
        syncRunner: suspend (
            WorkspaceConfig,
            File,
            (SyncProgressStep) -> Unit
        ) -> Result<SyncResult>
    ): AppSyncViewModel {
        val dispatcher = Dispatchers.Main
        val credentials = FakeCredentialStore()
        val workspaceStore = FakeWorkspaceStore()
        val loadSyncStatus = LoadSyncStatus(remote, dispatcher)
        val preflight = BuildSyncPreflight(remote, credentials, dispatcher)
        return AppSyncViewModel(
            config = WorkspaceConfig(
                name = "Test",
                relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
                remoteUri = "file:///tmp/remote.git",
                branch = branch,
                setupCompletedAtEpochMs = 0L
            ),
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
                workspaceStore,
                dispatcher
            ),
            manualSyncNow = ManualSyncNow(
                remoteSyncRepository = remote,
                credentialStore = credentials,
                registryCache = NoteRegistryCache(FakeRegistryRepository()),
                loadSyncStatus = loadSyncStatus,
                dispatcher = dispatcher
            ),
            recordLastSyncAttempt = RecordLastSyncAttempt(workspaceStore),
            syncRunner = syncRunner
        )
    }

    private fun successResult() = SyncResult(
        status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = branch,
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

    private class LocalChangesRemoteSyncRepository :
        RemoteSyncRepository by FakeRemoteSyncRepository() {

        override fun status(workingDir: File): Result<GitWorkspaceStatus> = Result.success(
            GitWorkspaceStatus(
                branch = "main",
                hasUncommittedChanges = true,
                changedPaths = setOf("Inbox/local.md")
            )
        )

        override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
            SyncChangePartition(changedPaths, emptySet(), emptySet())
    }

    private class RemoteBehindSyncRepository : RemoteSyncRepository by FakeRemoteSyncRepository() {

        override fun compareWithRemote(
            workingDir: File,
            branch: String
        ): Result<RemoteBranchComparison> = Result.success(
            RemoteBranchComparison(
                aheadCount = 0,
                behindCount = 1,
                isEqual = false,
                localIsAncestorOfRemote = true,
                remoteIsAncestorOfLocal = false,
                isDiverged = false
            )
        )
    }
}
