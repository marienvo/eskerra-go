package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.RemoteSyncSettings
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository

class LoadRemoteSyncSettings(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(config: WorkspaceConfig): RemoteSyncSettings =
        settingsRepository.readSettings(config)
}
