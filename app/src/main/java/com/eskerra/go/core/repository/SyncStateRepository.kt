package com.eskerra.go.core.repository

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Persistently tracks monotonic sync generations and durable sync state.
 */
interface SyncStateRepository {
    val record: Flow<DurableSyncRecord>

    suspend fun getRecord(): DurableSyncRecord

    /**
     * Atomically increments requestedGeneration by 1 and transitions to Pending if not currently Running/Retrying.
     * Returns the incremented requestedGeneration.
     */
    suspend fun markGenerationRequested(): Long

    /**
     * Updates completedGeneration up to [snapshot].
     * If completedGeneration >= requestedGeneration, transitions status to Synced([completedAtEpochMs]).
     * Otherwise transitions status to Pending.
     */
    suspend fun markGenerationCompleted(snapshot: Long, completedAtEpochMs: Long)

    /**
     * Updates durable execution status directly (e.g. Running, Retrying, Blocked).
     */
    suspend fun updateStatus(status: DurableSyncStatus)

    /**
     * Updates the running step text if and only if the current status is Running.
     * No-op if current status is not Running (e.g. Synced, Blocked, Retrying, Pending).
     */
    suspend fun updateRunningStep(step: String)

    /**
     * Reconciles orphaned Running status on app start:
     * If status is Running and [hasActiveWorker] is false, transitions to Pending.
     */
    suspend fun reconcileOrphanedRunning(hasActiveWorker: Boolean)
}
