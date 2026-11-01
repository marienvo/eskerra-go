package com.eskerra.go.data.sync

import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.VaultSyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodicVaultSyncWorkerTest {
    @Test
    fun syncedStateRequestsNewGeneration() = runTest {
        val state = FakeSyncStateRepository()
        val scheduler = CountingScheduler()
        requestPeriodicSync(state, scheduler)
        assertEquals(1L, state.getRecord().requestedGeneration)
        assertEquals(1, scheduler.reconciliations)
    }

    @Test
    fun pendingRequestIsReconciledWithoutAnotherGeneration() = runTest {
        val state = FakeSyncStateRepository()
        state.markGenerationRequested()
        val scheduler = CountingScheduler()
        requestPeriodicSync(state, scheduler)
        assertEquals(1L, state.getRecord().requestedGeneration)
        assertEquals(1, scheduler.reconciliations)
    }

    @Test
    fun blockedSyncIsNotRetriedAutomatically() = runTest {
        val state = FakeSyncStateRepository()
        state.updateStatus(DurableSyncStatus.Blocked("manual intervention"))
        val scheduler = CountingScheduler()
        requestPeriodicSync(state, scheduler)
        assertEquals(0L, state.getRecord().requestedGeneration)
        assertEquals(0, scheduler.reconciliations)
    }

    private class CountingScheduler : VaultSyncScheduler {
        var reconciliations = 0
        override fun scheduleSync() = Unit
        override fun cancelSync() = Unit
        override suspend fun reconcile() {
            reconciliations++
        }
    }
}
