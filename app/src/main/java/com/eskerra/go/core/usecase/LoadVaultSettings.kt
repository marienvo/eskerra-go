package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.repository.VaultSettingsRepository
import java.io.File

class LoadVaultSettings(private val repository: VaultSettingsRepository) {
    suspend operator fun invoke(workspaceRoot: File): Result<EskerraSettings> =
        repository.loadShared(workspaceRoot)
}
