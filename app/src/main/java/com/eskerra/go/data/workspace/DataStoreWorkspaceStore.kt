package com.eskerra.go.data.workspace

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.WorkspaceConfig
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
}

/** Preferences DataStore-backed [WorkspaceStore]. Non-secret metadata only. */
class DataStoreWorkspaceStore(private val dataStore: DataStore<Preferences>) : WorkspaceStore {

    constructor(context: Context) : this(context.applicationContext.workspaceDataStore)

    companion object {
        /** Keys persisted by this store. No token, password, or credential keys. */
        val NON_SECRET_PREFERENCE_KEY_NAMES = setOf(
            "workspace_name",
            "workspace_relative_path",
            "workspace_remote_uri",
            "workspace_branch",
            "workspace_setup_completed_at"
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
