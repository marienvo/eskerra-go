package com.eskerra.go.data.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.VaultSettingsError
import com.eskerra.go.core.model.VaultSettingsException
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.vault.EskerraSettingsCodec
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.core.vault.VaultLayout
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val Context.vaultSharedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_shared_settings"
)

private val SHARED_JSON_KEY = stringPreferencesKey("shared_settings_json")

private const val LEGACY_DISPLAY_NAME_KEY = "displayName"

/**
 * Implements §1.2 source-of-truth resolution:
 * - Read shared: workspace file if it exists → else DataStore.
 * - Write shared: create/update the file if R2 is configured or file already exists
 *   (the sub-decision to create the file on first R2 save) → else DataStore.
 * - Migrations: .notebox → .eskerra rename; legacy settings.json → settings-shared.json;
 *   legacy shared `displayName` → local settings (when local is empty), then stripped
 *   from the shared file (spec "migrateLegacySharedDisplayNameIfNeeded").
 */
class FileVaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val localSettingsStore: LocalSettingsStore
) : VaultSettingsRepository {

    constructor(context: Context) : this(
        context.applicationContext.vaultSharedDataStore,
        DataStoreLocalSettingsStore(context)
    )

    constructor(context: Context, localSettingsStore: LocalSettingsStore) : this(
        context.applicationContext.vaultSharedDataStore,
        localSettingsStore
    )

    override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> {
        migrateNoteboxIfNeeded(workspaceRoot)

        val eskerraDir = File(workspaceRoot, VaultLayout.ESKERRA_DIR)
        val sharedFile = File(eskerraDir, VaultLayout.SHARED_SETTINGS_FILE)
        val legacyFile = File(eskerraDir, VaultLayout.LEGACY_SETTINGS_FILE)

        return when {
            sharedFile.isFile -> readSharedFile(sharedFile)
            legacyFile.isFile -> migrateLegacySettings(legacyFile, sharedFile)
            else -> loadFromDataStore()
        }
    }

    override suspend fun saveShared(workspaceRoot: File, settings: EskerraSettings): Result<Unit> {
        migrateNoteboxIfNeeded(workspaceRoot)

        val eskerraDir = File(workspaceRoot, VaultLayout.ESKERRA_DIR)
        val sharedFile = File(eskerraDir, VaultLayout.SHARED_SETTINGS_FILE)
        val writeToFile = sharedFile.isFile || R2Settings.isVaultR2PlaylistConfigured(settings)

        return if (writeToFile) {
            saveToFile(eskerraDir, sharedFile, settings)
        } else {
            saveToDataStore(settings)
        }
    }

    private suspend fun readSharedFile(sharedFile: File): Result<EskerraSettings> {
        val raw = runCatching { sharedFile.readText() }.getOrElse {
            return Result.failure(
                VaultSettingsException(
                    VaultSettingsError.IoError("Failed to read ${sharedFile.name}.")
                )
            )
        }
        val settings = EskerraSettingsCodec.parse(raw).getOrElse { return Result.failure(it) }
        migrateLegacyDisplayName(raw, settings, sharedFile)
        return Result.success(settings)
    }

    private suspend fun migrateLegacySettings(
        legacyFile: File,
        sharedFile: File
    ): Result<EskerraSettings> {
        val raw = runCatching { legacyFile.readText() }.getOrElse {
            return Result.failure(
                VaultSettingsException(
                    VaultSettingsError.IoError("Failed to read ${legacyFile.name}.")
                )
            )
        }
        val settings = EskerraSettingsCodec.parse(raw).getOrElse { return Result.failure(it) }
        runCatching {
            sharedFile.parentFile?.mkdirs()
            sharedFile.writeText(EskerraSettingsCodec.serialize(settings))
        }
        migrateLegacyDisplayName(raw, settings, sharedFile)
        return Result.success(settings)
    }

    /**
     * Spec `migrateLegacySharedDisplayNameIfNeeded`: if the raw shared JSON still carries a
     * legacy `displayName`, copy it to local settings when the local name is empty, then
     * rewrite the shared file without the key. [settings] is already parsed without the key
     * (the codec ignores it), so re-serializing strips it from disk.
     */
    private suspend fun migrateLegacyDisplayName(
        raw: String,
        settings: EskerraSettings,
        sharedFile: File
    ) {
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        if (!obj.containsKey(LEGACY_DISPLAY_NAME_KEY)) return

        val legacyName = (obj[LEGACY_DISPLAY_NAME_KEY] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
        if (!legacyName.isNullOrEmpty()) {
            val local = localSettingsStore.load()
            if (local.displayName.isEmpty()) {
                localSettingsStore.save(local.copy(displayName = legacyName))
            }
        }

        runCatching { sharedFile.writeText(EskerraSettingsCodec.serialize(settings)) }
    }

    private suspend fun loadFromDataStore(): Result<EskerraSettings> {
        val json = dataStore.data.map { prefs -> prefs[SHARED_JSON_KEY] }.first()
        if (json.isNullOrBlank()) return Result.success(EskerraSettings())
        return EskerraSettingsCodec.parse(json)
    }

    private fun saveToFile(
        eskerraDir: File,
        sharedFile: File,
        settings: EskerraSettings
    ): Result<Unit> = runCatching {
        eskerraDir.mkdirs()
        sharedFile.writeText(EskerraSettingsCodec.serialize(settings))
    }.mapFailure {
        VaultSettingsException(
            VaultSettingsError.IoError("Failed to write settings-shared.json.")
        )
    }

    private suspend fun saveToDataStore(settings: EskerraSettings): Result<Unit> = runCatching {
        val json = EskerraSettingsCodec.serialize(settings)
        dataStore.edit { prefs -> prefs[SHARED_JSON_KEY] = json }
        Unit
    }.mapFailure {
        VaultSettingsException(VaultSettingsError.IoError("Failed to save settings."))
    }

    private fun migrateNoteboxIfNeeded(workspaceRoot: File) {
        val noteboxDir = File(workspaceRoot, VaultLayout.NOTEBOX_DIR)
        val eskerraDir = File(workspaceRoot, VaultLayout.ESKERRA_DIR)
        if (noteboxDir.isDirectory && !eskerraDir.exists()) {
            noteboxDir.renameTo(eskerraDir)
        }
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    this.recoverCatching { throw transform(it) }
