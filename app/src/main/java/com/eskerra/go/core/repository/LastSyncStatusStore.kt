package com.eskerra.go.core.repository

import com.eskerra.go.core.model.LastSyncStatus

/** Persists the latest manual sync attempt (non-secret fields only). */
interface LastSyncStatusStore {
    suspend fun readLastSyncStatus(): LastSyncStatus?

    suspend fun saveLastSyncStatus(status: LastSyncStatus)
}
