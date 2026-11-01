package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceMutationMutexTest {
    @Test
    fun setupWaitsForTheSharedVaultMutex() = runBlocking {
        val mutex = GitSyncMutex()
        val called = CompletableDeferred<Unit>()
        val setup = object : WorkspaceSetupRepository {
            override suspend fun completeSetup(
                mode: WorkspaceSetupMode,
                name: String,
                branch: String,
                remoteUri: String?,
                credential: String?,
                filesDir: File
            ): Result<WorkspaceConfig> {
                called.complete(Unit)
                return Result.failure(IllegalStateException("stop"))
            }
        }
        val completion = DefaultWorkspaceSetupCompletion(
            setup,
            FakeWorkspaceStore(),
            FakeCredentialStore(),
            mutex
        )
        mutex.mutex.lock()
        val request = async(Dispatchers.Default) {
            completion.completeAndPersist(
                WorkspaceSetupMode.InitializeLocal,
                "v",
                "main",
                null,
                null,
                File(".")
            )
        }
        assertNull(withTimeoutOrNull(100) { called.await() })
        mutex.mutex.unlock()
        withTimeout(5_000) {
            called.await()
            request.await()
        }
        Unit
    }

    @Test
    fun settingsWaitForMutexAndRejectStaleWorkspace() = runBlocking {
        val mutex = GitSyncMutex()
        val called = CompletableDeferred<Unit>()
        val repository = DefaultRemoteSyncSettingsRepository(
            FakeWorkspaceStore(),
            FakeCredentialStore(),
            JGitRemoteSyncRepository(JGitWorkspaceRepository()),
            mutex,
            isCurrentWorkspace = {
                called.complete(Unit)
                false
            }
        )
        val config = WorkspaceConfig("v", "vault", null, "main", 1L)
        mutex.mutex.lock()
        val request = async(Dispatchers.Default) {
            repository.saveSettings(
                config,
                "https://example.com/repo.git",
                "main",
                "token",
                File(".")
            )
        }
        assertNull(withTimeoutOrNull(100) { called.await() })
        mutex.mutex.unlock()
        withTimeout(5_000) {
            called.await()
            assertFalse(request.await().isSuccess)
        }
    }
}
