package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.workspace.FakeWorkspaceStore
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReconcileWorkspaceConfigTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun returnsStoredConfigWhenRemoteIsNotConfigured() = runTest {
        val filesDir = temp.newFolder("files")
        val reconcile = ReconcileWorkspaceSyncBranch(
            workspaceStore = FakeWorkspaceStore(),
            credentialStore = FakeCredentialStore(),
            remoteSyncRepository = FakeRemoteSyncRepository()
        )

        val result = reconcileWorkspaceConfig(
            config = config,
            filesDir = filesDir,
            reconcileWorkspaceSyncBranch = reconcile
        )

        assertEquals(config, result)
    }
}
