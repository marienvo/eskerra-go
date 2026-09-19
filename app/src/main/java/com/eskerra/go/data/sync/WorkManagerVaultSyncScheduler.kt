package com.eskerra.go.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerVaultSyncScheduler(
    private val context: Context,
    private val syncStateRepository: SyncStateRepository,
    private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(context) }
) : VaultSyncScheduler {

    override fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<VaultSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_VAULT_SYNC)
            .build()

        workManagerProvider().enqueueUniqueWork(
            WORK_NAME_VAULT_SYNC,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelSync() {
        workManagerProvider().cancelUniqueWork(WORK_NAME_VAULT_SYNC)
    }

    override suspend fun reconcile() {
        val record = syncStateRepository.getRecord()
        val workInfos = try {
            workManagerProvider().getWorkInfosForUniqueWork(WORK_NAME_VAULT_SYNC).get()
        } catch (_: Exception) {
            emptyList()
        }
        val isWorkerActive = workInfos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (!isWorkerActive) {
            if (record.status is DurableSyncStatus.Running) {
                syncStateRepository.updateStatus(DurableSyncStatus.Pending)
                scheduleSync()
            } else if (record.requestedGeneration > record.completedGeneration) {
                if (record.status !is DurableSyncStatus.Blocked) {
                    syncStateRepository.updateStatus(DurableSyncStatus.Pending)
                    scheduleSync()
                }
            }
        }
    }

    companion object {
        const val WORK_NAME_VAULT_SYNC = "vault_sync"
        const val TAG_VAULT_SYNC = "vault_sync_worker"
        const val BACKOFF_DELAY_SECONDS = 10L
    }
}
