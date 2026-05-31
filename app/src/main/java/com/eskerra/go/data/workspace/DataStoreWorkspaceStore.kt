package com.eskerra.go.data.workspace

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.LastSyncStatus
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LastSyncStatusStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workspaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "workspace_config"
)

private object WorkspacePreferenceKeys {
    val name = stringPreferencesKey("workspace_name")
    val relativePath = stringPreferencesKey("workspace_relative_path")
    val remoteUri = stringPreferencesKey("workspace_remote_uri")
    val branch = stringPreferencesKey("workspace_branch")
    val setupCompletedAt = longPreferencesKey("workspace_setup_completed_at")
    val lastSyncAttemptAt = longPreferencesKey("last_sync_attempt_at")
    val lastSyncOutcome = stringPreferencesKey("last_sync_outcome")
    val lastSyncErrorCategory = stringPreferencesKey("last_sync_error_category")
}

/** Preferences DataStore-backed [WorkspaceStore] and [LastSyncStatusStore]. Non-secret metadata only. */
class DataStoreWorkspaceStore(private val dataStore: DataStore<Preferences>) :
    WorkspaceStore,
    LastSyncStatusStore {

    constructor(context: Context) : this(context.applicationContext.workspaceDataStore)

    companion object {
        /** Keys persisted by this store. No token, password, or credential keys. */
        val NON_SECRET_PREFERENCE_KEY_NAMES = setOf(
            "workspace_name",
            "workspace_relative_path",
            "workspace_remote_uri",
            "workspace_branch",
            "workspace_setup_completed_at",
            "last_sync_attempt_at",
            "last_sync_outcome",
            "last_sync_error_category"
        )
    }

    override suspend fun read(): WorkspaceConfig? {
        val prefs = dataStore.data.map { it.toWorkspaceConfig() }.first()
        return prefs
    }

    override suspend fun save(config: WorkspaceConfig) {
        dataStore.edit { prefs ->
            prefs[WorkspacePreferenceKeys.name] = config.name
            prefs[WorkspacePreferenceKeys.relativePath] = config.relativePath
            prefs[WorkspacePreferenceKeys.remoteUri] = config.remoteUri.orEmpty()
            prefs[WorkspacePreferenceKeys.branch] = config.branch
            prefs[WorkspacePreferenceKeys.setupCompletedAt] = config.setupCompletedAtEpochMs
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    override suspend fun readLastSyncStatus(): LastSyncStatus? {
        val prefs = dataStore.data.first()
        val attemptedAt = prefs[WorkspacePreferenceKeys.lastSyncAttemptAt] ?: return null
        val outcomeRaw = prefs[WorkspacePreferenceKeys.lastSyncOutcome] ?: return null
        val outcome = runCatching {
            com.eskerra.go.core.model.SyncAttemptOutcome.valueOf(outcomeRaw)
        }.getOrNull() ?: return null
        val errorCategory = prefs[WorkspacePreferenceKeys.lastSyncErrorCategory]
        return LastSyncStatus(
            attemptedAtEpochMs = attemptedAt,
            outcome = outcome,
            errorCategory = errorCategory
        )
    }

    override suspend fun saveLastSyncStatus(status: LastSyncStatus) {
        dataStore.edit { prefs ->
            prefs[WorkspacePreferenceKeys.lastSyncAttemptAt] = status.attemptedAtEpochMs
            prefs[WorkspacePreferenceKeys.lastSyncOutcome] = status.outcome.name
            if (status.errorCategory.isNullOrBlank()) {
                prefs.remove(WorkspacePreferenceKeys.lastSyncErrorCategory)
            } else {
                prefs[WorkspacePreferenceKeys.lastSyncErrorCategory] = status.errorCategory
            }
        }
    }

    private fun Preferences.toWorkspaceConfig(): WorkspaceConfig? {
        val name = this[WorkspacePreferenceKeys.name] ?: return null
        val relativePath = this[WorkspacePreferenceKeys.relativePath] ?: return null
        val branch = this[WorkspacePreferenceKeys.branch] ?: return null
        val setupCompletedAt = this[WorkspacePreferenceKeys.setupCompletedAt] ?: return null
        val remoteUriRaw = this[WorkspacePreferenceKeys.remoteUri].orEmpty()
        return WorkspaceConfig(
            name = name,
            relativePath = relativePath,
            remoteUri = remoteUriRaw.ifBlank { null },
            branch = branch,
            setupCompletedAtEpochMs = setupCompletedAt
        )
    }
}
