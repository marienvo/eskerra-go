package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository
import java.io.File

class SaveRemoteSyncSettings(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        remoteUri: String,
        branch: String,
        replacementToken: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = settingsRepository.saveSettings(
        config = config,
        remoteUri = remoteUri,
        branch = branch,
        replacementToken = replacementToken,
        filesDir = filesDir
    )
}
