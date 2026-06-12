package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.SearchOutcome
import com.eskerra.go.core.repository.VaultSearchRepository
import java.io.File

class SearchVault(private val repository: VaultSearchRepository) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        query: String,
        searchId: Long
    ): Result<SearchOutcome> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val status = repository.status(config, filesDir)
            return Result.success(
                SearchOutcome(
                    searchId = searchId,
                    vaultInstanceId = status.vaultInstanceId,
                    notes = emptyList(),
                    status = status
                )
            )
        }
        return repository.search(config, filesDir, trimmed, searchId)
    }
}
