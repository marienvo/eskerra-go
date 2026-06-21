package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastRefreshProgress
import com.eskerra.go.core.repository.PodcastRssVaultSync
import com.eskerra.go.core.repository.PodcastRssVaultSyncSummary
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPodcastVaultRefreshTest {

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val filesDir = File("/tmp/unused")

    @Test
    fun `runs vault sync then commits and combines the result`() = runTest {
        val committed = PodcastSyncResult(
            committed = true,
            commitId = "abc",
            pushed = true,
            pendingPush = false
        )
        val useCase = SyncPodcastVaultRefresh(
            vaultSync = object : PodcastRssVaultSync {
                override suspend fun refresh(
                    config: WorkspaceConfig,
                    filesDir: File,
                    onProgress: (PodcastRefreshProgress) -> Unit
                ) = Result.success(PodcastRssVaultSyncSummary(2, 1, 0))
            },
            syncPodcastChange = { _, _ -> Result.success(committed) },
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = useCase(config, filesDir).getOrThrow()

        assertEquals(2, result.rss.refreshedFileCount)
        assertEquals(committed, result.sync)
    }

    @Test
    fun `refresh succeeds when best-effort sync fails after a local merge`() = runTest {
        val useCase = SyncPodcastVaultRefresh(
            vaultSync = object : PodcastRssVaultSync {
                override suspend fun refresh(
                    config: WorkspaceConfig,
                    filesDir: File,
                    onProgress: (PodcastRefreshProgress) -> Unit
                ) = Result.success(PodcastRssVaultSyncSummary(2, 1, 0))
            },
            syncPodcastChange = { _, _ ->
                Result.failure(
                    com.eskerra.go.core.model.SyncException(
                        com.eskerra.go.core.model.SyncError.MissingRemoteConfig
                    )
                )
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = useCase(config, filesDir).getOrThrow()

        assertEquals(2, result.rss.refreshedFileCount)
        assertTrue(result.sync.pendingPush)
    }

    @Test
    fun `concurrent callers coalesce into a single in-flight run`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val refreshCount = AtomicInteger(0)
        val commitCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val useCase = SyncPodcastVaultRefresh(
            vaultSync = object : PodcastRssVaultSync {
                override suspend fun refresh(
                    config: WorkspaceConfig,
                    filesDir: File,
                    onProgress: (PodcastRefreshProgress) -> Unit
                ): Result<PodcastRssVaultSyncSummary> {
                    refreshCount.incrementAndGet()
                    gate.await()
                    return Result.success(PodcastRssVaultSyncSummary.EMPTY)
                }
            },
            syncPodcastChange = { _, _ ->
                commitCount.incrementAndGet()
                Result.success(PodcastSyncResult.NOTHING_TO_COMMIT)
            },
            dispatcher = dispatcher
        )

        val first = async { useCase(config, filesDir) }
        val second = async { useCase(config, filesDir) }
        runCurrent()
        gate.complete(Unit)

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, refreshCount.get())
        assertEquals(1, commitCount.get())
    }
}
