package com.eskerra.go.data.sync

import com.eskerra.go.core.repository.VaultSyncScheduler

class FakeVaultSyncScheduler : VaultSyncScheduler {
    var scheduledCount = 0
    var cancelledCount = 0

    override fun scheduleSync() {
        scheduledCount++
    }

    override fun cancelSync() {
        cancelledCount++
    }
}
