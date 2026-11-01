package com.eskerra.go.core.repository

/**
 * Enqueues durable vault synchronization.
 */
interface VaultSyncScheduler {
    fun scheduleSync()
    fun scheduleBackgroundSync() = scheduleSync()
    fun cancelSync()
    suspend fun reconcile()
    suspend fun reconcileBackground() = reconcile()
}

class NoOpVaultSyncScheduler : VaultSyncScheduler {
    override fun scheduleSync() {}
    override fun cancelSync() {}
    override suspend fun reconcile() {}
}
