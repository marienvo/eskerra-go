package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.SyncAttemptOutcome
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSyncStatusStoreTest {

    private val store = FakeWorkspaceStore()
    private val record = RecordLastSyncAttempt(store)

    @Test
    fun saveLastSyncStatus_persistsOnlySafeFields() = runTest {
        record.recordFailure(SyncError.AuthenticationFailed)

        val saved = store.readLastSyncStatus()

        assertTrue(saved != null)
        assertEquals(SyncAttemptOutcome.Failed, saved!!.outcome)
        assertEquals("AuthenticationFailed", saved.errorCategory)
        assertTrue(saved.attemptedAtEpochMs > 0L)
    }

    @Test
    fun partialSuccess_persistsPartialOutcome() = runTest {
        record.recordSuccess(
            SyncResult(
                status = SyncStatusSummary(
                    state = SyncStatusState.Clean,
                    branch = "main",
                    changedCount = 0,
                    aheadCount = 0,
                    behindCount = 0,
                    message = "Up to date."
                ),
                committed = false,
                commitId = null,
                pushed = false,
                pulled = false,
                registryRefreshed = false
            )
        )

        val saved = store.readLastSyncStatus()

        assertEquals(SyncAttemptOutcome.PartialSuccess, saved?.outcome)
        assertEquals("RegistryRefreshFailed", saved?.errorCategory)
    }

    @Test
    fun success_clearsErrorCategory() = runTest {
        record.recordSuccess(
            SyncResult(
                status = SyncStatusSummary(
                    state = SyncStatusState.Clean,
                    branch = "main",
                    changedCount = 0,
                    aheadCount = 0,
                    behindCount = 0,
                    message = "Up to date."
                ),
                committed = false,
                commitId = null,
                pushed = false,
                pulled = false,
                registryRefreshed = true
            )
        )

        val saved = store.readLastSyncStatus()

        assertEquals(SyncAttemptOutcome.Success, saved?.outcome)
        assertNull(saved?.errorCategory)
    }

    @Test
    fun savedFields_doNotContainTokenLikeSecrets() = runTest {
        record.recordFailure(SyncError.AuthenticationFailed)

        val json = store.readLastSyncStatus().toString()

        assertFalse(json.contains("token", ignoreCase = true))
        assertFalse(json.contains("password", ignoreCase = true))
    }
}
