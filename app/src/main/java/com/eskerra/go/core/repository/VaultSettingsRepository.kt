package com.eskerra.go.core.repository

import com.eskerra.go.core.model.EskerraSettings
import java.io.File

interface VaultSettingsRepository {
    suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings>
    suspend fun saveShared(workspaceRoot: File, settings: EskerraSettings): Result<Unit>
}
