package com.eskerra.go.data.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler

class FakeVaultSyncScheduler(
    private val syncStateRepository: SyncStateRepository? = null,
    private val onSchedule: (() -> Unit)? = null
) : VaultSyncScheduler {
    var scheduledCount = 0
    var cancelledCount = 0
    var reconcileCount = 0

    override fun scheduleSync() {
        scheduledCount++
        onSchedule?.invoke()
    }

    override fun cancelSync() {
        cancelledCount++
    }

    override suspend fun reconcile() {
        reconcileCount++
        syncStateRepository?.let { repo ->
            val record = repo.getRecord()
            if (record.status is DurableSyncStatus.Running) {
                repo.updateStatus(DurableSyncStatus.Pending)
                scheduleSync()
            } else if (record.requestedGeneration > record.completedGeneration) {
                if (record.status !is DurableSyncStatus.Blocked) {
                    repo.updateStatus(DurableSyncStatus.Pending)
                    scheduleSync()
                }
            }
        }
    }
}
