package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPodcastChangesViaVaultSyncTest {

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = "https://example.com/repo.git",
        branch = "main",
        setupCompletedAtEpochMs = 0L
    )
    private val filesDir = File("/tmp/unused")

    private fun adapter(result: Result<SyncResult>) =
        SyncPodcastChangesViaVaultSync(runVaultSync = { _, _ -> result })

    private fun syncResult(committed: Boolean, pushed: Boolean) = SyncResult(
        status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Up to date."
        ),
        committed = committed,
        commitId = if (committed) "abc123" else null,
        pushed = pushed,
        pulled = false
    )

    @Test
    fun committedAndPushed_mapsToPushedResult() = runTest {
        val result = adapter(Result.success(syncResult(committed = true, pushed = true)))(
            config, filesDir
        ).getOrThrow()

        assertTrue(result.committed)
        assertTrue(result.pushed)
        assertFalse(result.pendingPush)
        assertEquals("abc123", result.commitId)
    }

    @Test
    fun committedButNotPushed_marksPendingPush() = runTest {
        val result = adapter(Result.success(syncResult(committed = true, pushed = false)))(
            config, filesDir
        ).getOrThrow()

        assertTrue(result.committed)
        assertFalse(result.pushed)
        assertTrue(result.pendingPush)
    }

    @Test
    fun alreadyRunning_isDeferredAsPendingPush() = runTest {
        val result = adapter(
            Result.failure(SyncException(SyncError.SyncAlreadyRunning))
        )(config, filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().pendingPush)
        assertFalse(result.getOrThrow().committed)
    }

    @Test
    fun remoteUnavailable_isDeferredAsPendingPush() = runTest {
        val result = adapter(
            Result.failure(SyncException(SyncError.RemoteUnavailable))
        )(config, filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().pendingPush)
    }

    @Test
    fun pushRejected_isDeferredAsPendingPush() = runTest {
        val result = adapter(
            Result.failure(SyncException(SyncError.PushRejected))
        )(config, filesDir)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().pendingPush)
    }

    @Test
    fun missingCredential_propagatesFailure() = runTest {
        val result = adapter(
            Result.failure(SyncException(SyncError.MissingCredential))
        )(config, filesDir)

        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as SyncException).error is SyncError.MissingCredential)
    }
}
