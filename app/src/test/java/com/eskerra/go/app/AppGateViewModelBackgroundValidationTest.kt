package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.FakeBootCacheStore
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppGateViewModelBackgroundValidationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

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
    fun optimisticReady_downgradesWhenWorkspaceRemovedBeforeBackgroundValidation() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()

        val store = FakeWorkspaceStore()
        store.save(config)
        val bootCache = FakeBootCacheStore()
        bootCache.saveFingerprint(GateFingerprintComputer.compute(config, filesDir))
        workspaceDir.deleteRecursively()

        val viewModel = AppGateViewModel(
            workspaceStore = store,
            bootCacheStore = bootCache,
            filesDir = filesDir,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        assertTrue(viewModel.gateState.value is AppGateState.NeedsSetup)
    }
}
