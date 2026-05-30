package com.eskerra.go.app

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
}
