package com.eskerra.go.core.repository

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.search.VaultSearchIndexStatus
import com.eskerra.go.core.search.VaultSearchNoteResult
import java.io.File

interface VaultSearchRepository {
    suspend fun status(config: WorkspaceConfig, filesDir: File): VaultSearchIndexStatus

    suspend fun maintain(config: WorkspaceConfig, filesDir: File): Result<Unit>

    suspend fun touchPaths(
        config: WorkspaceConfig,
        filesDir: File,
        paths: List<String>
    ): Result<Unit>

    suspend fun search(
        config: WorkspaceConfig,
        filesDir: File,
        query: String,
        searchId: Long
    ): Result<SearchOutcome>

    /** Deletes the local FTS index file and rebuilds title indexing from scratch. */
    suspend fun repairIndex(config: WorkspaceConfig, filesDir: File): Result<Unit>
}

data class SearchOutcome(
    val searchId: Long,
    val vaultInstanceId: String,
    val notes: List<VaultSearchNoteResult>,
    val status: VaultSearchIndexStatus
)
