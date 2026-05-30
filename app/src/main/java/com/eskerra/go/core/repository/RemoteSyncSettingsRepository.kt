package com.eskerra.go.core.repository

import com.eskerra.go.core.model.RemoteSyncSettings
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Persists sanitized remote sync metadata and coordinates Git origin configuration. */
interface RemoteSyncSettingsRepository {
    suspend fun readSettings(config: WorkspaceConfig): RemoteSyncSettings

    suspend fun saveSettings(
        config: WorkspaceConfig,
        remoteUri: String,
        branch: String,
        replacementToken: String?,
        filesDir: File
    ): Result<WorkspaceConfig>

    suspend fun replaceToken(relativePath: String, token: String): Result<Unit>

    suspend fun clearSettings(config: WorkspaceConfig, filesDir: File): Result<WorkspaceConfig>

    suspend fun testConnection(config: WorkspaceConfig, filesDir: File): Result<Unit>
}
