package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.LastSyncStatus
import com.eskerra.go.core.model.SyncAttemptOutcome
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.repository.LastSyncStatusStore

/** Persists the latest non-secret sync attempt outcome. */
class RecordLastSyncAttempt(private val lastSyncStatusStore: LastSyncStatusStore) {

    suspend fun recordSuccess(result: SyncResult) {
        val outcome = if (result.registryRefreshed) {
            SyncAttemptOutcome.Success
        } else {
            SyncAttemptOutcome.PartialSuccess
        }
        val errorCategory = if (result.registryRefreshed) {
            null
        } else {
            SyncError.RegistryRefreshFailed.categoryName()
        }
        lastSyncStatusStore.saveLastSyncStatus(
            LastSyncStatus(
                attemptedAtEpochMs = System.currentTimeMillis(),
                outcome = outcome,
                errorCategory = errorCategory
            )
        )
    }

    suspend fun recordFailure(error: SyncError) {
        lastSyncStatusStore.saveLastSyncStatus(
            LastSyncStatus(
                attemptedAtEpochMs = System.currentTimeMillis(),
                outcome = SyncAttemptOutcome.Failed,
                errorCategory = error.categoryName()
            )
        )
    }
}
