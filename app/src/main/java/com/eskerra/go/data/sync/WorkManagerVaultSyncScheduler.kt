package com.eskerra.go.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerVaultSyncScheduler(
    private val context: Context,
    private val syncStateRepository: SyncStateRepository,
    private val enqueueWork: (
        name: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest
    ) -> Unit = { name, policy, request ->
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    },
    private val cancelWork: (name: String) -> Unit = { name ->
        WorkManager.getInstance(context).cancelUniqueWork(name)
    },
    private val enqueuePeriodicWork: (PeriodicWorkRequest) -> Unit = { request ->
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_vault_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    },
    private val getActiveWorkInfos: suspend (name: String) -> List<WorkInfo> = { name ->
        try {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
        } catch (_: Exception) {
            emptyList()
        }
    }
) : VaultSyncScheduler {

    override fun scheduleSync() = enqueueSync(background = false)

    override fun scheduleBackgroundSync() = enqueueSync(background = true)

    private fun enqueueSync(background: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<VaultSyncWorker>()
            .setInputData(workDataOf(VaultSyncWorker.KEY_BACKGROUND to background))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_VAULT_SYNC)
            .build()

        enqueueWork(
            WORK_NAME_VAULT_SYNC,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    override fun cancelSync() {
        cancelWork(WORK_NAME_VAULT_SYNC)
    }

    override suspend fun reconcile() = reconcileInternal(background = false)

    override suspend fun reconcileBackground() = reconcileInternal(background = true)

    private suspend fun reconcileInternal(background: Boolean) {
        schedulePeriodicSync()
        val record = syncStateRepository.getRecord()
        val workInfos = getActiveWorkInfos(WORK_NAME_VAULT_SYNC)
        val isWorkerActive = workInfos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (!isWorkerActive) {
            if (record.status is DurableSyncStatus.Running) {
                syncStateRepository.updateStatus(DurableSyncStatus.Pending)
                enqueueSync(background)
            } else if (record.requestedGeneration > record.completedGeneration) {
                if (record.status !is DurableSyncStatus.Blocked) {
                    syncStateRepository.updateStatus(DurableSyncStatus.Pending)
                    enqueueSync(background)
                }
            }
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PeriodicVaultSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        enqueuePeriodicWork(request)
    }

    companion object {
        const val WORK_NAME_VAULT_SYNC = "vault_sync"
        const val TAG_VAULT_SYNC = "vault_sync_worker"
        const val BACKOFF_DELAY_SECONDS = 10L
    }
}
