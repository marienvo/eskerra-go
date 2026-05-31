package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository
import java.io.File

class TestRemoteConnection(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        remoteUri: String? = null,
        branch: String? = null,
        replacementToken: String? = null
    ): Result<Unit> = settingsRepository.testConnection(
        config = config,
        filesDir = filesDir,
        remoteUri = remoteUri,
        branch = branch,
        replacementToken = replacementToken
    )
}
