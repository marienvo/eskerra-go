package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppGateViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L,
    )

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshGate_withNoConfig_movesToNeedsSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val viewModel = AppGateViewModel(FakeWorkspaceStore(), filesDir)

        viewModel.refreshGate()

        assertEquals(AppGateState.NeedsSetup(), viewModel.gateState.value)
    }

    @Test
    fun refreshGate_withValidConfig_movesToReady() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, ".git").mkdir()

        val store = FakeWorkspaceStore()
        store.save(config)
        val viewModel = AppGateViewModel(store, filesDir)

        viewModel.refreshGate()

        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    @Test
    fun markReady_movesToReadyWithoutSaving() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FakeWorkspaceStore()
        val viewModel = AppGateViewModel(store, filesDir)

        viewModel.markReady(config)

        assertEquals(null, store.read())
        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    @Test
    fun refreshGate_afterPersistedConfig_skipsSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, ".git").mkdir()

        val store = FakeWorkspaceStore()
        store.save(config)
        val viewModel = AppGateViewModel(store, filesDir)

        viewModel.refreshGate()

        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }
}
