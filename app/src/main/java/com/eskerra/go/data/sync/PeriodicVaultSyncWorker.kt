package com.eskerra.go.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler

/** Requests a fresh vault sync through the existing serialized worker queue. */
class PeriodicVaultSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val runtime = (applicationContext as? SyncRuntimeProvider)?.syncRuntime
            ?: return Result.failure()
        val config = runtime.workspaceStore.read() ?: return Result.success()
        if (config.remoteUri.isNullOrBlank()) return Result.success()
        requestPeriodicSync(runtime.syncStateRepository, runtime.vaultSyncScheduler)
        return Result.success()
    }
}

internal suspend fun requestPeriodicSync(
    state: SyncStateRepository,
    scheduler: VaultSyncScheduler
) {
    val record = state.getRecord()
    if (record.status is DurableSyncStatus.Blocked) return
    if (!record.isPending) state.markGenerationRequested()
    scheduler.reconcileBackground()
}
