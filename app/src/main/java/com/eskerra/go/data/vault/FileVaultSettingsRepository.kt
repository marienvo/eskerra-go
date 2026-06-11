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
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.vault.EskerraSettingsCodec
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.core.vault.VaultLayout
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultSharedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_shared_settings"
)

private val SHARED_JSON_KEY = stringPreferencesKey("shared_settings_json")

/**
 * Implements §1.2 source-of-truth resolution:
 * - Read shared: workspace file if it exists → else DataStore.
 * - Write shared: create/update the file if R2 is configured or file already exists
 *   (the sub-decision to create the file on first R2 save) → else DataStore.
 * - Migrations: .notebox → .eskerra rename; legacy settings.json → settings-shared.json.
 */
class FileVaultSettingsRepository(private val dataStore: DataStore<Preferences>) :
    VaultSettingsRepository {

    constructor(context: Context) : this(context.applicationContext.vaultSharedDataStore)

    override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> {
        migrateNoteboxIfNeeded(workspaceRoot)

        val eskerraDir = File(workspaceRoot, VaultLayout.ESKERRA_DIR)
        val sharedFile = File(eskerraDir, VaultLayout.SHARED_SETTINGS_FILE)
        val legacyFile = File(eskerraDir, VaultLayout.LEGACY_SETTINGS_FILE)

        return when {
            sharedFile.isFile -> parseFile(sharedFile)
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

    private fun parseFile(file: File): Result<EskerraSettings> {
        val raw = runCatching { file.readText() }.getOrElse {
            return Result.failure(
                VaultSettingsException(VaultSettingsError.IoError("Failed to read ${file.name}."))
            )
        }
        return EskerraSettingsCodec.parse(raw)
    }

    private fun migrateLegacySettings(legacyFile: File, sharedFile: File): Result<EskerraSettings> {
        val result = parseFile(legacyFile)
        val settings = result.getOrElse { return result }
        val serialized = EskerraSettingsCodec.serialize(settings)
        runCatching { sharedFile.writeText(serialized) }
        return Result.success(settings)
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
