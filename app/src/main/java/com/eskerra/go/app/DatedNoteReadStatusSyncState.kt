package com.eskerra.go.app

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus

internal fun isDatedReadStatusSyncing(
    requestedGeneration: Long?,
    record: DurableSyncRecord?
): Boolean {
    val generation = requestedGeneration ?: return false
    if (record == null || record.requestedGeneration < generation) return true
    if (record.completedGeneration >= generation) return false
    return record.status !is DurableSyncStatus.Blocked &&
        record.status !is DurableSyncStatus.Retrying
}

internal fun isDatedReadStatusSyncFailed(
    requestedGeneration: Long,
    record: DurableSyncRecord
): Boolean = record.requestedGeneration >= requestedGeneration &&
    record.completedGeneration < requestedGeneration &&
    (record.status is DurableSyncStatus.Blocked || record.status is DurableSyncStatus.Retrying)
