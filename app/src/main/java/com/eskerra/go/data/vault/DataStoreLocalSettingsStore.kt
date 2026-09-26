package com.eskerra.go.data.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.repository.LocalSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultLocalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_local_settings"
)

private object Keys {
    val displayName = stringPreferencesKey("display_name")
    val deviceName = stringPreferencesKey("device_name")
    val deviceInstanceId = stringPreferencesKey("device_instance_id")
}

/** DataStore-backed local settings. Always per-device; never synced via git. */
class DataStoreLocalSettingsStore(private val dataStore: DataStore<Preferences>) :
    LocalSettingsStore {

    constructor(context: Context) : this(context.applicationContext.vaultLocalDataStore)

    override suspend fun load(): EskerraLocalSettings = dataStore.data.map { prefs ->
        EskerraLocalSettings(
            displayName = prefs[Keys.displayName].orEmpty(),
            deviceName = prefs[Keys.deviceName].orEmpty(),
            deviceInstanceId = prefs[Keys.deviceInstanceId].orEmpty()
        )
    }.first()

    override suspend fun save(settings: EskerraLocalSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.displayName] = settings.displayName
            prefs[Keys.deviceName] = settings.deviceName
            prefs[Keys.deviceInstanceId] = settings.deviceInstanceId
        }
    }
}
