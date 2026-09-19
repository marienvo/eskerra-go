package com.eskerra.go.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.repository.SyncStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_state"
)

class DataStoreSyncStateRepository(private val dataStore: DataStore<Preferences>) :
    SyncStateRepository {

    constructor(context: Context) : this(context.applicationContext.syncStateDataStore)

    override val record: Flow<DurableSyncRecord> = dataStore.data.map { prefs ->
        prefs.toDurableSyncRecord()
    }

    override suspend fun getRecord(): DurableSyncRecord = record.first()

    override suspend fun markGenerationRequested(): Long {
        var newRequested = 0L
        dataStore.edit { prefs ->
            val currentRequested = prefs[KEY_REQUESTED_GENERATION] ?: 0L
            newRequested = currentRequested + 1L
            prefs[KEY_REQUESTED_GENERATION] = newRequested

            val currentStatus = prefs.toDurableSyncRecord().status
            if (currentStatus !is DurableSyncStatus.Running &&
                currentStatus !is DurableSyncStatus.Retrying
            ) {
                prefs.writeStatus(DurableSyncStatus.Pending)
            }
        }
        return newRequested
    }

    override suspend fun markGenerationCompleted(snapshot: Long, completedAtEpochMs: Long) {
        dataStore.edit { prefs ->
            val currentCompleted = prefs[KEY_COMPLETED_GENERATION] ?: 0L
            val newCompleted = maxOf(currentCompleted, snapshot)
            prefs[KEY_COMPLETED_GENERATION] = newCompleted

            val requested = prefs[KEY_REQUESTED_GENERATION] ?: 0L
            if (newCompleted >= requested) {
                prefs.writeStatus(DurableSyncStatus.Synced(completedAtEpochMs))
            } else {
                prefs.writeStatus(DurableSyncStatus.Pending)
            }
        }
    }

    override suspend fun updateStatus(status: DurableSyncStatus) {
        dataStore.edit { prefs ->
            prefs.writeStatus(status)
        }
    }

    override suspend fun reconcileOrphanedRunning(hasActiveWorker: Boolean) {
        dataStore.edit { prefs ->
            val currentRecord = prefs.toDurableSyncRecord()
            if (currentRecord.status is DurableSyncStatus.Running && !hasActiveWorker) {
                prefs.writeStatus(DurableSyncStatus.Pending)
            }
        }
    }

    private companion object {
        val KEY_REQUESTED_GENERATION = longPreferencesKey("requested_generation")
        val KEY_COMPLETED_GENERATION = longPreferencesKey("completed_generation")
        val KEY_STATUS_TYPE = stringPreferencesKey("status_type")
        val KEY_STATUS_PARAM_STRING = stringPreferencesKey("status_param_string")
        val KEY_STATUS_PARAM_LONG = longPreferencesKey("status_param_long")

        const val TYPE_PENDING = "PENDING"
        const val TYPE_RUNNING = "RUNNING"
        const val TYPE_RETRYING = "RETRYING"
        const val TYPE_SYNCED = "SYNCED"
        const val TYPE_BLOCKED = "BLOCKED"

        fun Preferences.toDurableSyncRecord(): DurableSyncRecord {
            val requested = this[KEY_REQUESTED_GENERATION] ?: 0L
            val completed = this[KEY_COMPLETED_GENERATION] ?: 0L
            val type = this[KEY_STATUS_TYPE] ?: TYPE_SYNCED
            val paramString = this[KEY_STATUS_PARAM_STRING] ?: ""
            val paramLong = this[KEY_STATUS_PARAM_LONG] ?: 0L

            val status = when (type) {
                TYPE_PENDING -> DurableSyncStatus.Pending
                TYPE_RUNNING -> DurableSyncStatus.Running(paramString, paramLong)
                TYPE_RETRYING -> DurableSyncStatus.Retrying(paramString, paramLong)
                TYPE_BLOCKED -> DurableSyncStatus.Blocked(paramString)
                else -> DurableSyncStatus.Synced(paramLong)
            }
            return DurableSyncRecord(
                requestedGeneration = requested,
                completedGeneration = completed,
                status = status
            )
        }

        fun MutablePreferences.writeStatus(status: DurableSyncStatus) {
            when (status) {
                is DurableSyncStatus.Pending -> {
                    this[KEY_STATUS_TYPE] = TYPE_PENDING
                    remove(KEY_STATUS_PARAM_STRING)
                    remove(KEY_STATUS_PARAM_LONG)
                }
                is DurableSyncStatus.Running -> {
                    this[KEY_STATUS_TYPE] = TYPE_RUNNING
                    this[KEY_STATUS_PARAM_STRING] = status.step
                    this[KEY_STATUS_PARAM_LONG] = status.startedAtEpochMs
                }
                is DurableSyncStatus.Retrying -> {
                    this[KEY_STATUS_TYPE] = TYPE_RETRYING
                    this[KEY_STATUS_PARAM_STRING] = status.reason
                    this[KEY_STATUS_PARAM_LONG] = status.nextAttemptAtEpochMs
                }
                is DurableSyncStatus.Synced -> {
                    this[KEY_STATUS_TYPE] = TYPE_SYNCED
                    remove(KEY_STATUS_PARAM_STRING)
                    this[KEY_STATUS_PARAM_LONG] = status.completedAtEpochMs
                }
                is DurableSyncStatus.Blocked -> {
                    this[KEY_STATUS_TYPE] = TYPE_BLOCKED
                    this[KEY_STATUS_PARAM_STRING] = status.reason
                    remove(KEY_STATUS_PARAM_LONG)
                }
            }
        }
    }
}
