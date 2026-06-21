package com.eskerra.go.data.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.AppShellMode
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
    val playlistKnownUpdatedAtMs = longPreferencesKey("playlist_known_updated_at_ms")
    val playlistKnownControlRevision = longPreferencesKey("playlist_known_control_revision")
    val lastShellMode = stringPreferencesKey("last_shell_mode")
    val podcastEpisodeId = stringPreferencesKey("podcast_episode_id")
    val podcastMp3Url = stringPreferencesKey("podcast_mp3_url")
    val podcastPositionMs = longPreferencesKey("podcast_position_ms")
    val podcastDurationMs = longPreferencesKey("podcast_duration_ms")
    val podcastSnapshotUpdatedAtMs = longPreferencesKey("podcast_snapshot_updated_at_ms")
}

private const val NULL_LONG = -1L

/** DataStore-backed local settings. Always per-device; never synced via git. */
class DataStoreLocalSettingsStore(private val dataStore: DataStore<Preferences>) :
    LocalSettingsStore {

    constructor(context: Context) : this(context.applicationContext.vaultLocalDataStore)

    override suspend fun load(): EskerraLocalSettings = dataStore.data.map { prefs ->
        EskerraLocalSettings(
            displayName = prefs[Keys.displayName].orEmpty(),
            deviceName = prefs[Keys.deviceName].orEmpty(),
            deviceInstanceId = prefs[Keys.deviceInstanceId].orEmpty(),
            playlistKnownUpdatedAtMs = prefs[Keys.playlistKnownUpdatedAtMs]
                ?.takeIf { it != NULL_LONG },
            playlistKnownControlRevision = prefs[Keys.playlistKnownControlRevision]
                ?.takeIf { it != NULL_LONG },
            lastShellMode = AppShellMode.fromStored(prefs[Keys.lastShellMode]),
            podcastEpisodeId = prefs[Keys.podcastEpisodeId],
            podcastMp3Url = prefs[Keys.podcastMp3Url],
            podcastPositionMs = prefs[Keys.podcastPositionMs]?.takeIf { it != NULL_LONG },
            podcastDurationMs = prefs[Keys.podcastDurationMs]?.takeIf { it != NULL_LONG },
            podcastSnapshotUpdatedAtMs = prefs[Keys.podcastSnapshotUpdatedAtMs]
                ?.takeIf { it != NULL_LONG }
        )
    }.first()

    override suspend fun save(settings: EskerraLocalSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.displayName] = settings.displayName
            prefs[Keys.deviceName] = settings.deviceName
            prefs[Keys.deviceInstanceId] = settings.deviceInstanceId
            prefs[Keys.playlistKnownUpdatedAtMs] =
                settings.playlistKnownUpdatedAtMs ?: NULL_LONG
            prefs[Keys.playlistKnownControlRevision] =
                settings.playlistKnownControlRevision ?: NULL_LONG
            prefs[Keys.lastShellMode] = settings.lastShellMode.name
            if (settings.podcastEpisodeId == null) {
                prefs.remove(Keys.podcastEpisodeId)
                prefs.remove(Keys.podcastMp3Url)
                prefs.remove(Keys.podcastPositionMs)
                prefs.remove(Keys.podcastDurationMs)
                prefs.remove(Keys.podcastSnapshotUpdatedAtMs)
            } else {
                prefs[Keys.podcastEpisodeId] = settings.podcastEpisodeId
                prefs[Keys.podcastMp3Url] = settings.podcastMp3Url.orEmpty()
                prefs[Keys.podcastPositionMs] = settings.podcastPositionMs ?: NULL_LONG
                prefs[Keys.podcastDurationMs] = settings.podcastDurationMs ?: NULL_LONG
                prefs[Keys.podcastSnapshotUpdatedAtMs] =
                    settings.podcastSnapshotUpdatedAtMs ?: NULL_LONG
            }
        }
    }
}
