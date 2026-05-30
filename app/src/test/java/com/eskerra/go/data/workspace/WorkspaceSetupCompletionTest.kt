package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceSetupCompletionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepository = JGitWorkspaceRepository()
    private val setupRepository = DefaultWorkspaceSetupRepository(gitRepository)

    private fun completion(
        workspaceStore: WorkspaceStore = FakeWorkspaceStore(),
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
    ) = DefaultWorkspaceSetupCompletion(
        setupRepository = setupRepository,
        workspaceStore = workspaceStore,
        credentialStore = credentialStore,
    )

    @Test
    fun completeAndPersist_savesMetadataAndCredentialSeparately() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val credentialStore = FakeCredentialStore()

        val result = completion(workspaceStore, credentialStore).completeAndPersist(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Notes",
            branch = "",
            remoteUri = null,
            credential = "secret-token",
            filesDir = filesDir,
        )

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals("Notes", workspaceStore.read()?.name)
        assertEquals("secret-token", credentialStore.tokens[WorkspacePaths.DEFAULT_RELATIVE_PATH])
        assertFalse(config.toString().contains("secret-token"))
    }

    @Test
    fun completeAndPersist_metadataSaveFailure_returnsErrorAndDoesNotAdvance() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FailingWorkspaceStore()
        val credentialStore = FakeCredentialStore()

        val result = completion(workspaceStore, credentialStore).completeAndPersist(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Notes",
            branch = "",
            remoteUri = null,
            credential = null,
            filesDir = filesDir,
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.MetadataSaveFailed)
        assertNull(workspaceStore.read())
    }

    @Test
    fun completeAndPersist_metadataSaveFailure_clearsSavedCredential() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FailingWorkspaceStore()
        val credentialStore = FakeCredentialStore()

        val result = completion(workspaceStore, credentialStore).completeAndPersist(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Notes",
            branch = "",
            remoteUri = null,
            credential = "secret-token",
            filesDir = filesDir,
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.MetadataSaveFailed)
        assertNull(workspaceStore.read())
        assertNull(credentialStore.tokens[WorkspacePaths.DEFAULT_RELATIVE_PATH])
    }

    @Test
    fun completeAndPersist_credentialNotStoredInWorkspaceConfig() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceStore = FakeWorkspaceStore()
        val credentialStore = FakeCredentialStore()

        completion(workspaceStore, credentialStore).completeAndPersist(
            mode = WorkspaceSetupMode.InitializeLocal,
            name = "Notes",
            branch = "",
            remoteUri = null,
            credential = "pat-12345",
            filesDir = filesDir,
        )

        val saved = workspaceStore.read()
        requireNotNull(saved)
        assertFalse(DataStoreWorkspaceStore.NON_SECRET_PREFERENCE_KEY_NAMES.any { key ->
            key.contains("token", ignoreCase = true) || key.contains("credential", ignoreCase = true)
        })
        assertFalse(saved.remoteUri?.contains("pat-12345") == true)
    }
}
