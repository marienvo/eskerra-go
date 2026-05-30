package com.eskerra.go.data.workspace

import com.eskerra.go.app.AppGateState
import com.eskerra.go.core.model.WorkspaceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppGateResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val validConfig = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun nullConfig_returnsNeedsSetup() {
        val filesDir = temp.newFolder("files")
        val state = resolveAppGateState(null, filesDir)

        assertEquals(AppGateState.NeedsSetup(), state)
    }

    @Test
    fun invalidRelativePath_returnsNeedsSetupWithMessage() {
        val filesDir = temp.newFolder("files")
        val config = validConfig.copy(relativePath = "../escape")

        val state = resolveAppGateState(config, filesDir)

        assertTrue(state is AppGateState.NeedsSetup)
        assertTrue((state as AppGateState.NeedsSetup).recoveryMessage != null)
    }

    @Test
    fun missingGitRepo_returnsNeedsSetup() {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()

        val state = resolveAppGateState(validConfig, filesDir)

        assertTrue(state is AppGateState.NeedsSetup)
        assertEquals(
            "Workspace repository is missing or invalid",
            (state as AppGateState.NeedsSetup).recoveryMessage,
        )
    }

    @Test
    fun validConfigAndGitRepo_returnsReady() {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, ".git").mkdir()

        val state = resolveAppGateState(validConfig, filesDir)

        assertEquals(AppGateState.Ready(validConfig), state)
    }
}
