package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.FakeBootCacheStore
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
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

    private val testDispatcher = UnconfinedTestDispatcher()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withNoConfig_movesToNeedsSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val viewModel = appGateViewModel(FakeWorkspaceStore(), filesDir)

        assertEquals(AppGateState.NeedsSetup(), viewModel.gateState.value)
    }

    @Test
    fun init_withValidConfig_movesToReady() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()

        val store = FakeWorkspaceStore()
        store.save(config)
        val viewModel = appGateViewModel(store, filesDir)

        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    @Test
    fun markReady_movesToReadyWithoutSaving() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FakeWorkspaceStore()
        val viewModel = appGateViewModel(store, filesDir)

        viewModel.markReady(config)

        assertEquals(null, store.read())
        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    @Test
    fun init_withMissingWorkspace_showsRecoverableSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FakeWorkspaceStore()
        store.save(config)
        val viewModel = appGateViewModel(store, filesDir)

        val state = viewModel.gateState.value as AppGateState.NeedsSetup
        assertEquals(
            "Workspace files are missing. Set up again to recover.",
            state.recoveryMessage
        )
    }

    @Test
    fun init_afterPersistedConfig_skipsSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()

        val store = FakeWorkspaceStore()
        store.save(config)
        val viewModel = appGateViewModel(store, filesDir)

        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    @Test
    fun init_withMatchingFingerprint_skipsLocalFilesystemGate() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()

        val store = FakeWorkspaceStore()
        store.save(config)
        val bootCache = FakeBootCacheStore()
        bootCache.saveFingerprint(GateFingerprintComputer.compute(config, filesDir))

        val viewModel = appGateViewModel(store, filesDir, bootCache)

        assertEquals(AppGateState.Ready(config), viewModel.gateState.value)
    }

    private fun appGateViewModel(
        workspaceStore: FakeWorkspaceStore,
        filesDir: File,
        bootCacheStore: FakeBootCacheStore = FakeBootCacheStore()
    ): AppGateViewModel = AppGateViewModel(
        workspaceStore = workspaceStore,
        bootCacheStore = bootCacheStore,
        filesDir = filesDir,
        ioDispatcher = testDispatcher
    )
}
