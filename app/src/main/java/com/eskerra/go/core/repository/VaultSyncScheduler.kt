package com.eskerra.go.core.repository

/**
 * Enqueues durable vault synchronization.
 */
interface VaultSyncScheduler {
    fun scheduleSync()
    fun cancelSync()
}
