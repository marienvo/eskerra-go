package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.VaultSearchRepository
import java.io.File

class TouchVaultSearchPaths(private val repository: VaultSearchRepository) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        paths: List<String>
    ): Result<Unit> = repository.touchPaths(config, filesDir, paths)
}
