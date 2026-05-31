package com.eskerra.go.app

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.FailingNoteRegistryRepository
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.sync.SyncUiState
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            val viewModel = createViewModel(ioDispatcher, onSyncSuccess = { inboxRefreshed = true })

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Success)
            assertTrue(inboxRefreshed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun doubleSyncNow_startsOnlyOneSync() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val slowRegistry = FakeNoteRegistryRepository()
            slowRegistry.setRefreshDelayMs(1_000)
            val viewModel = createViewModel(ioDispatcher, registry = slowRegistry)

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()

            viewModel.syncNow()
            viewModel.syncNow()
            advanceUntilIdle()

            assertEquals(1, slowRegistry.refreshCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun registryRefreshFailure_emitsSuccessWithWarning() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val failingRegistry = FailingNoteRegistryRepository(FakeNoteRegistryRepository())
            val viewModel = createViewModel(ioDispatcher, registry = failingRegistry)

            viewModel.refreshRemoteStatus(force = true)
            advanceUntilIdle()
            viewModel.syncNow()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SyncUiState.Success)
            assertNotNull((state as SyncUiState.Success).warningMessage)
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

    private fun createViewModel(
        ioDispatcher: CoroutineDispatcher,
        registry: com.eskerra.go.core.repository.NoteRegistryRepository = FakeRegistryRepository(),
        onSyncSuccess: () -> Unit = {},
        clock: () -> Long = { 0L }
    ): AppSyncViewModel {
        val status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Up to date."
        )
        val fakeRemote = FetchTrackingFakeRemoteSyncRepository(
            inner = FakeRemoteSyncRepository(status),
            fetchCount = fetchCount
        )
        val credentials = FakeCredentialStore()
        val lastSyncStore = FakeWorkspaceStore()
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
            filesDir = filesDir,
            loadSyncStatus = loadSyncStatus,
            refreshRemoteSyncStatus = refreshRemoteSyncStatus,
            buildSyncPreflight = buildPreflight,
            buildSafeSyncDiagnostic = buildDiagnostic,
            manualSyncNow = ManualSyncNow(
                remoteSyncRepository = fakeRemote,
                credentialStore = credentials,
                registryRepository = registry,
                loadSyncStatus = loadSyncStatus,
                dispatcher = ioDispatcher
            ),
            recordLastSyncAttempt = RecordLastSyncAttempt(lastSyncStore),
            onSyncSuccess = onSyncSuccess,
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
