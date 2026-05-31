package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository
import java.io.File

class ClearRemoteSyncSettings(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): Result<WorkspaceConfig> =
        settingsRepository.clearSettings(config, filesDir)
}
