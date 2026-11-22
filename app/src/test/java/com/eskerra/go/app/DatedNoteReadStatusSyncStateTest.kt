package com.eskerra.go.app

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatedNoteReadStatusSyncStateTest {
    @Test
    fun failureIsDetectedWithoutDiscardingLocalState() {
        val blocked = DurableSyncRecord(
            requestedGeneration = 2,
            completedGeneration = 1,
            status = DurableSyncStatus.Blocked("offline")
        )

        assertTrue(isDatedReadStatusSyncFailed(2, blocked))
        assertFalse(isDatedReadStatusSyncFailed(2, blocked.copy(requestedGeneration = 1)))
    }
}
