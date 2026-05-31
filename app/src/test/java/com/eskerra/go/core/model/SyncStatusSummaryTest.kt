package com.eskerra.go.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusSummaryTest {

    @Test
    fun hasSyncWork_false_whenClean() {
        val status = SyncStatusSummary(
            state = SyncStatusState.Clean,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Up to date."
        )

        assertFalse(status.hasSyncWork)
        assertFalse(status.needsAttention)
    }

    @Test
    fun hasSyncWork_false_whenUnavailable() {
        assertFalse(SyncStatusSummary.unavailable.hasSyncWork)
    }

    @Test
    fun hasSyncWork_true_whenDirtyLocalChanges() {
        val status = SyncStatusSummary(
            state = SyncStatusState.DirtyLocalChanges,
            branch = "main",
            changedCount = 1,
            aheadCount = 0,
            behindCount = 0,
            message = "Local changes."
        )

        assertTrue(status.hasSyncWork)
    }

    @Test
    fun hasSyncWork_true_whenBehind() {
        val status = SyncStatusSummary(
            state = SyncStatusState.Behind,
            branch = "main",
            changedCount = 0,
            aheadCount = 0,
            behindCount = 2,
            message = "Remote has changes."
        )

        assertTrue(status.hasSyncWork)
    }
}
