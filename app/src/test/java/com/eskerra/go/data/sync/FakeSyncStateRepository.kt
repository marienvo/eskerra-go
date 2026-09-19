package com.eskerra.go.data.sync

import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSyncStateRepository(initialRecord: DurableSyncRecord = DurableSyncRecord()) :
    SyncStateRepository {

    private val _record = MutableStateFlow(initialRecord)
    override val record: Flow<DurableSyncRecord> = _record.asStateFlow()

    override suspend fun getRecord(): DurableSyncRecord = _record.value

    override suspend fun markGenerationRequested(): Long {
        var newRequested = 0L
        _record.update { current ->
            newRequested = current.requestedGeneration + 1L
            val nextStatus = if (current.status !is DurableSyncStatus.Running &&
                current.status !is DurableSyncStatus.Retrying
            ) {
                DurableSyncStatus.Pending
            } else {
                current.status
            }
            current.copy(
                requestedGeneration = newRequested,
                status = nextStatus
            )
        }
        return newRequested
    }

    override suspend fun markGenerationCompleted(snapshot: Long, completedAtEpochMs: Long) {
        _record.update { current ->
            val newCompleted = maxOf(current.completedGeneration, snapshot)
            val nextStatus = if (newCompleted >= current.requestedGeneration) {
                DurableSyncStatus.Synced(completedAtEpochMs)
            } else {
                DurableSyncStatus.Pending
            }
            current.copy(
                completedGeneration = newCompleted,
                status = nextStatus
            )
        }
    }

    override suspend fun updateStatus(status: DurableSyncStatus) {
        _record.update { it.copy(status = status) }
    }

    override suspend fun updateRunningStep(step: String) {
        _record.update { current ->
            val running = current.status as? DurableSyncStatus.Running
            if (running != null) {
                current.copy(
                    status = DurableSyncStatus.Running(
                        step = step,
                        startedAtEpochMs = running.startedAtEpochMs
                    )
                )
            } else {
                current
            }
        }
    }

    override suspend fun reconcileOrphanedRunning(hasActiveWorker: Boolean) {
        _record.update { current ->
            if (current.status is DurableSyncStatus.Running && !hasActiveWorker) {
                current.copy(status = DurableSyncStatus.Pending)
            } else {
                current
            }
        }
    }
}
