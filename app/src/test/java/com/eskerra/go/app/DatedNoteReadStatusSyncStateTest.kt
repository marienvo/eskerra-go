package com.eskerra.go.app

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatedNoteReadStatusSyncStateTest {
    @Test
    fun waitsForTheRequestedGeneration() {
        assertTrue(isDatedReadStatusSyncing(2, null))
        assertTrue(isDatedReadStatusSyncing(2, DurableSyncRecord(requestedGeneration = 1)))
        assertTrue(
            isDatedReadStatusSyncing(
                2,
                DurableSyncRecord(
                    requestedGeneration = 2,
                    completedGeneration = 1,
                    status = DurableSyncStatus.Pending
                )
            )
        )
        assertFalse(
            isDatedReadStatusSyncing(
                2,
                DurableSyncRecord(requestedGeneration = 2, completedGeneration = 2)
            )
        )
    }

    @Test
    fun failureStopsSpinnerWithoutDiscardingLocalState() {
        val blocked = DurableSyncRecord(
            requestedGeneration = 2,
            completedGeneration = 1,
            status = DurableSyncStatus.Blocked("offline")
        )

        assertFalse(isDatedReadStatusSyncing(2, blocked))
        assertTrue(isDatedReadStatusSyncFailed(2, blocked))
        assertFalse(isDatedReadStatusSyncFailed(2, blocked.copy(requestedGeneration = 1)))
        assertFalse(isDatedReadStatusSyncing(null, blocked))
    }
}
