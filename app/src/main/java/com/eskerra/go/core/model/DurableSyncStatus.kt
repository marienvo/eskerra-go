package com.eskerra.go.core.model

sealed interface DurableSyncStatus {
    data object Pending : DurableSyncStatus
    data class Running(val step: String, val startedAtEpochMs: Long) : DurableSyncStatus
    data class Retrying(val reason: String, val nextAttemptAtEpochMs: Long) : DurableSyncStatus
    data class Synced(val completedAtEpochMs: Long) : DurableSyncStatus
    data class Blocked(val reason: String) : DurableSyncStatus
}

data class DurableSyncRecord(
    val requestedGeneration: Long = 0L,
    val completedGeneration: Long = 0L,
    val status: DurableSyncStatus = DurableSyncStatus.Synced(0L)
) {
    val isPending: Boolean get() = requestedGeneration > completedGeneration
}
