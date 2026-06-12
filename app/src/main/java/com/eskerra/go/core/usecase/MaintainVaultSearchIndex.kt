package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.VaultSearchRepository
import java.io.File

class MaintainVaultSearchIndex(private val repository: VaultSearchRepository) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): Result<Unit> =
        repository.maintain(config, filesDir)
}
