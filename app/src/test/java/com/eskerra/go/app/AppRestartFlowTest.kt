package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.data.workspace.resolveAppGateState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** JVM-level restart simulation for the app gate after workspace setup. */
class AppRestartFlowTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
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
    fun configuredWorkspaceWithGit_skipsSetupAfterRestart() = runTest {
        val filesDir = temp.newFolder("files")
        prepareValidWorkspace(filesDir)

        val store = FakeWorkspaceStore()
        store.save(config)

        val firstLaunch = AppGateViewModel(store, filesDir)
        assertEquals(AppGateState.Ready(config), firstLaunch.gateState.value)

        val secondLaunch = AppGateViewModel(store, filesDir)
        assertEquals(AppGateState.Ready(config), secondLaunch.gateState.value)
    }

    @Test
    fun missingMetadata_showsSetupOnRestart() = runTest {
        val filesDir = temp.newFolder("files")
        prepareValidWorkspace(filesDir)

        val store = FakeWorkspaceStore()
        val viewModel = AppGateViewModel(store, filesDir)

        assertEquals(AppGateState.NeedsSetup(), viewModel.gateState.value)
    }

    @Test
    fun metadataWithMissingWorkspace_showsRecoverableSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val store = FakeWorkspaceStore()
        store.save(config)

        val state = resolveAppGateState(config, filesDir)

        assertTrue(state is AppGateState.NeedsSetup)
        assertEquals(
            "Workspace files are missing. Set up again to recover.",
            (state as AppGateState.NeedsSetup).recoveryMessage
        )
    }

    @Test
    fun metadataWithMissingGit_showsRecoverableSetup() = runTest {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val store = FakeWorkspaceStore()
        store.save(config)

        val state = resolveAppGateState(config, filesDir)

        assertTrue(state is AppGateState.NeedsSetup)
        assertEquals(
            "Workspace repository is missing or invalid. Set up again to recover.",
            (state as AppGateState.NeedsSetup).recoveryMessage
        )
    }

    @Test
    fun metadataWithEmptyGitDirectory_showsRecoverableSetup() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, ".git").mkdir()
        val store = FakeWorkspaceStore()
        store.save(config)

        val state = resolveAppGateState(config, filesDir)

        assertTrue(state is AppGateState.NeedsSetup)
        assertEquals(
            "Workspace repository is missing or invalid. Set up again to recover.",
            (state as AppGateState.NeedsSetup).recoveryMessage
        )
    }

    private fun prepareValidWorkspace(filesDir: File) {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }
}
