package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository
import java.io.File

class TestRemoteConnection(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): Result<Unit> =
        settingsRepository.testConnection(config, filesDir)
}
