package com.eskerra.go.core.repository

/**
 * Enqueues durable vault synchronization.
 */
interface VaultSyncScheduler {
    fun scheduleSync()
    fun cancelSync()
    suspend fun reconcile()
}

class NoOpVaultSyncScheduler : VaultSyncScheduler {
    override fun scheduleSync() {}
    override fun cancelSync() {}
    override suspend fun reconcile() {}
}
