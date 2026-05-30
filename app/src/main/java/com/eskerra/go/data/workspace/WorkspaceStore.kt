package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig

/** Persists the single workspace configuration (non-secret metadata only). */
interface WorkspaceStore {
    suspend fun read(): WorkspaceConfig?
    suspend fun save(config: WorkspaceConfig)
    suspend fun clear()
}
