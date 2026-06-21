package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastRefreshProgress
import com.eskerra.go.core.repository.PodcastRssVaultSync
import com.eskerra.go.core.repository.PodcastRssVaultSyncSummary
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of a vault refresh: the RSS write summary plus the refresh commit. */
data class PodcastVaultRefreshResult(
    val rss: PodcastRssVaultSyncSummary,
    val sync: PodcastSyncResult
)

/**
 * Pull-to-refresh entry point. Runs the native RSS vault sync and then commits the
 * resulting markdown changes as a single `Refresh podcast episodes` commit.
 *
 * At most **one** refresh runs at a time globally; concurrent callers await the
 * same in-flight run (spec §7.2). The coalesced run uses an internal supervisor
 * scope so a cancelled caller does not abort the shared work.
 */
class SyncPodcastVaultRefresh(
    private val vaultSync: PodcastRssVaultSync,
    private val syncPodcastChange: PodcastChangeSync,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private var inFlight: Deferred<Result<PodcastVaultRefreshResult>>? = null

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (PodcastRefreshProgress) -> Unit = {}
    ): Result<PodcastVaultRefreshResult> {
        val run = lock.withLock {
            inFlight ?: scope.async { runRefresh(config, filesDir, onProgress) }
                .also { inFlight = it }
        }
        return try {
            run.await()
        } finally {
            lock.withLock { if (inFlight === run) inFlight = null }
        }
    }

    private suspend fun runRefresh(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (PodcastRefreshProgress) -> Unit
    ): Result<PodcastVaultRefreshResult> {
        val summary = vaultSync.refresh(config, filesDir, onProgress)
            .getOrElse { return Result.failure(it) }
        // The RSS merge already landed in the working tree, so a failed best-effort sync
        // (no remote, missing credential, offline, push rejected) must not surface as
        // "could not refresh". The local commit rides the next successful vault sync.
        val sync = syncPodcastChange(config, filesDir).getOrElse { PodcastSyncResult.PENDING }
        return Result.success(PodcastVaultRefreshResult(rss = summary, sync = sync))
    }
}
