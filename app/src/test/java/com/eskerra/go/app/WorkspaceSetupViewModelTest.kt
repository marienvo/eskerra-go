package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceSetupMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceSetupViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun submit_metadataSaveFailure_showsErrorAndDoesNotCallSuccess() = runBlocking {
        val filesDir = temp.newFolder("files")
        val viewModel = WorkspaceSetupViewModel(
            setupCompletion = FailingWorkspaceSetupCompletion(),
            filesDir = filesDir,
            recoveryMessage = null
        )
        var successCalled = false

        viewModel.submit { successCalled = true }

        assertTrue(!successCalled)
        assertTrue(
            viewModel.uiState.errorMessage?.startsWith("Could not save workspace settings") == true
        )
    }

    @Test
    fun submit_success_clearsCredentialFromUiState() = runBlocking {
        val filesDir = temp.newFolder("files")
        val viewModel = WorkspaceSetupViewModel(
            setupCompletion = SuccessWorkspaceSetupCompletion(),
            filesDir = filesDir,
            recoveryMessage = null
        )
        viewModel.onNameChange("Notes")
        viewModel.onCredentialChange("pat-12345")

        viewModel.submit { }

        assertEquals("", viewModel.uiState.credential)
    }

    @Test
    fun submit_initializeLocalDoesNotForwardHiddenCredential() = runBlocking {
        val filesDir = temp.newFolder("files")
        val setupCompletion = RecordingWorkspaceSetupCompletion()
        val viewModel = WorkspaceSetupViewModel(
            setupCompletion = setupCompletion,
            filesDir = filesDir,
            recoveryMessage = null
        )
        viewModel.onModeChange(WorkspaceSetupMode.Clone)
        viewModel.onCredentialChange("pat-12345")
        viewModel.onModeChange(WorkspaceSetupMode.InitializeLocal)

        viewModel.submit { }

        assertEquals("", viewModel.uiState.credential)
        assertEquals(null, setupCompletion.lastCredential)
    }

    private class RecordingWorkspaceSetupCompletion : WorkspaceSetupCompletion {
        var lastCredential: String? = null

        override suspend fun completeAndPersist(
            mode: WorkspaceSetupMode,
            name: String,
            branch: String,
            remoteUri: String?,
            credential: String?,
            filesDir: java.io.File
        ): Result<WorkspaceConfig> {
            lastCredential = credential
            return Result.success(
                WorkspaceConfig(
                    name = name.trim().ifBlank { "Notes" },
                    relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
                    remoteUri = null,
                    branch = "master",
                    setupCompletedAtEpochMs = 1_700_000_000_000L
                )
            )
        }
    }
}
