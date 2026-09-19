package com.eskerra.go.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runtimeProvider = applicationContext as? SyncRuntimeProvider ?: return Result.failure()
        return performSync(
            syncRuntime = runtimeProvider.syncRuntime,
            setForegroundInfo = { setForeground(it) },
            attempt = runAttemptCount
        )
    }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 10
        const val INITIAL_BACKOFF_MS = 10_000L

        suspend fun performSync(
            syncRuntime: SyncRuntime,
            setForegroundInfo: suspend (ForegroundInfo) -> Unit,
            attempt: Int = 0,
            syncRunner: suspend (
                WorkspaceConfig,
                File,
                (SyncProgressStep) -> Unit
            ) -> kotlin.Result<SyncResult> = { config, filesDir, onProgress ->
                syncRuntime.manualSyncNow(config, filesDir, onProgress)
            }
        ): Result = coroutineScope {
            val currentRecord = syncRuntime.syncStateRepository.getRecord()
            if (!currentRecord.isPending && currentRecord.status is DurableSyncStatus.Synced) {
                return@coroutineScope Result.success()
            }

            syncRuntime.context?.let { ctx ->
                runCatching {
                    setForegroundInfo(
                        SyncNotificationHelper.buildForegroundInfo(
                            context = ctx,
                            stepText = "Eskerra synchroniseert…"
                        )
                    )
                }
            }

            val snapshotGeneration = currentRecord.requestedGeneration
            val config = syncRuntime.workspaceStore.read()
            if (config == null || config.remoteUri.isNullOrBlank()) {
                val error = SyncError.MissingRemoteConfig
                syncRuntime.syncStateRepository.updateStatus(
                    DurableSyncStatus.Blocked(error.message())
                )
                syncRuntime.recordLastSyncAttempt.recordFailure(error)
                return@coroutineScope Result.failure()
            }

            val preflight = syncRuntime.buildSyncPreflight(config, syncRuntime.filesDir)
            if (!preflight.canSync) {
                val error = SyncError.UnsafeLocalPath
                syncRuntime.syncStateRepository.updateStatus(
                    DurableSyncStatus.Blocked(error.message())
                )
                syncRuntime.recordLastSyncAttempt.recordFailure(error)
                return@coroutineScope Result.failure()
            }

            try {
                val startedAt = System.currentTimeMillis()
                syncRuntime.syncStateRepository.updateStatus(
                    DurableSyncStatus.Running(
                        step = SyncProgressStep.ValidatingWorkspace.name,
                        startedAtEpochMs = startedAt
                    )
                )

                val syncResult = syncRunner(
                    config,
                    syncRuntime.filesDir
                ) { step ->
                    launch {
                        syncRuntime.syncStateRepository.updateRunningStep(step.name)
                    }
                }

                syncResult.fold(
                    onSuccess = { result: SyncResult ->
                        result.updatedConfig?.let { updated ->
                            syncRuntime.workspaceStore.save(updated)
                        }
                        syncRuntime.recordLastSyncAttempt.recordSuccess(result)
                        val completedAt = System.currentTimeMillis()
                        syncRuntime.syncStateRepository.markGenerationCompleted(
                            snapshot = snapshotGeneration,
                            completedAtEpochMs = completedAt
                        )

                        // If newer generations arrived while syncing, trigger next round
                        val latest = syncRuntime.syncStateRepository.getRecord()
                        if (latest.requestedGeneration > snapshotGeneration) {
                            syncRuntime.vaultSyncScheduler.scheduleSync()
                        }
                        Result.success()
                    },
                    onFailure = { throwable ->
                        val syncError = when (throwable) {
                            is SyncException -> throwable.error
                            else -> SyncError.GitFailed(throwable.message ?: "Sync failed.")
                        }
                        syncRuntime.recordLastSyncAttempt.recordFailure(syncError)

                        if (isTransient(syncError) && attempt < MAX_RETRY_ATTEMPTS) {
                            val backoffMs = calculateBackoffMs(attempt)
                            syncRuntime.syncStateRepository.updateStatus(
                                DurableSyncStatus.Retrying(
                                    reason = syncError.message(),
                                    nextAttemptAtEpochMs = System.currentTimeMillis() + backoffMs
                                )
                            )
                            Result.retry()
                        } else {
                            syncRuntime.syncStateRepository.updateStatus(
                                DurableSyncStatus.Blocked(reason = syncError.message())
                            )
                            Result.failure()
                        }
                    }
                )
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    syncRuntime.syncStateRepository.updateStatus(DurableSyncStatus.Pending)
                }
                throw e
            }
        }

        fun isTransient(error: SyncError): Boolean = when (error) {
            SyncError.RemoteUnavailable,
            SyncError.PushRejected,
            SyncError.SyncAlreadyRunning,
            is SyncError.GitFailed -> true
            else -> false
        }

        fun calculateBackoffMs(attempt: Int): Long {
            val shift = minOf(attempt, 6)
            return INITIAL_BACKOFF_MS * (1L shl shift)
        }
    }
}
