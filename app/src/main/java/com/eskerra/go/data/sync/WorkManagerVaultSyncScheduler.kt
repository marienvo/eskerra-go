package com.eskerra.go.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eskerra.go.core.repository.VaultSyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerVaultSyncScheduler(
    private val context: Context,
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

    companion object {
        const val WORK_NAME_VAULT_SYNC = "vault_sync"
        const val TAG_VAULT_SYNC = "vault_sync_worker"
        const val BACKOFF_DELAY_SECONDS = 10L
    }
}
