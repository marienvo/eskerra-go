package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.LastSyncStatus
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LastSyncStatusStore

/** In-memory [WorkspaceStore] and [LastSyncStatusStore] for JVM tests. */
class FakeWorkspaceStore :
    WorkspaceStore,
    LastSyncStatusStore {
    private var config: WorkspaceConfig? = null
    private var lastSyncStatus: LastSyncStatus? = null

    override suspend fun read(): WorkspaceConfig? = config

    override suspend fun save(config: WorkspaceConfig) {
        this.config = config
    }

    override suspend fun clear() {
        config = null
        lastSyncStatus = null
    }

    override suspend fun readLastSyncStatus(): LastSyncStatus? = lastSyncStatus

    override suspend fun saveLastSyncStatus(status: LastSyncStatus) {
        lastSyncStatus = status
    }
}
