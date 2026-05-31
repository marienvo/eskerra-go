package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.RemoteSyncSettingsRepository

class UpdateSyncToken(private val settingsRepository: RemoteSyncSettingsRepository) {
    suspend operator fun invoke(relativePath: String, token: String): Result<Unit> =
        settingsRepository.replaceToken(relativePath, token)
}
