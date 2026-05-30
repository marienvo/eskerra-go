package com.eskerra.go.app

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.sync.SyncUiState
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
class SyncViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File

    @Before
    fun setUpWorkspace() {
        filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }

    @Test
    fun refreshStatus_emitsReadyState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val status = SyncStatusSummary(
                state = SyncStatusState.Clean,
                branch = "main",
                changedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                message = "Up to date."
            )
            val fakeRemote = FakeRemoteSyncRepository(status)
            val viewModel = SyncViewModel(
                config = testConfig(),
                filesDir = filesDir,
                loadSyncStatus = LoadSyncStatus(fakeRemote, ioDispatcher),
                manualSyncNow = ManualSyncNow(
                    remoteSyncRepository = fakeRemote,
                    credentialStore = com.eskerra.go.data.credentials.FakeCredentialStore(),
                    registryRepository = FakeRegistryRepository(),
                    loadSyncStatus = LoadSyncStatus(fakeRemote, ioDispatcher),
                    dispatcher = ioDispatcher
                )
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SyncUiState.Ready)
            assertEquals(status, (state as SyncUiState.Ready).status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNow_emitsSuccessState() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val status = SyncStatusSummary(
                state = SyncStatusState.Clean,
                branch = "main",
                changedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                message = "Up to date."
            )
            val fakeRemote = FakeRemoteSyncRepository(status)
            var inboxRefreshed = false
            val viewModel = SyncViewModel(
                config = testConfig(),
                filesDir = filesDir,
                loadSyncStatus = LoadSyncStatus(fakeRemote, ioDispatcher),
                manualSyncNow = ManualSyncNow(
                    remoteSyncRepository = fakeRemote,
                    credentialStore = com.eskerra.go.data.credentials.FakeCredentialStore(),
                    registryRepository = FakeRegistryRepository(),
                    loadSyncStatus = LoadSyncStatus(fakeRemote, ioDispatcher),
                    dispatcher = ioDispatcher
                ),
                onSyncSuccess = { inboxRefreshed = true }
            )
            advanceUntilIdle()

            viewModel.syncNow()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SyncUiState.Success)
            assertTrue(inboxRefreshed)
        } finally {
            Dispatchers.resetMain()
        }
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
}
