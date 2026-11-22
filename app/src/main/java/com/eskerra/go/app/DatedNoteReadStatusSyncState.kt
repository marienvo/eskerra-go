package com.eskerra.go.app

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus

internal fun isDatedReadStatusSyncFailed(
    requestedGeneration: Long,
    record: DurableSyncRecord
): Boolean = record.requestedGeneration >= requestedGeneration &&
    record.completedGeneration < requestedGeneration &&
    (record.status is DurableSyncStatus.Blocked || record.status is DurableSyncStatus.Retrying)
