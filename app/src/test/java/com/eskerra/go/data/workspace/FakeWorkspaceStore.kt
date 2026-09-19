package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.LastSyncStatus
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.BootCacheStore
import com.eskerra.go.core.repository.LastSyncStatusStore

/** In-memory [WorkspaceStore], [LastSyncStatusStore], and [BootCacheStore] for JVM tests. */
class FakeWorkspaceStore :
    WorkspaceStore,
    LastSyncStatusStore,
    BootCacheStore {
    private var config: WorkspaceConfig? = null
    private var lastSyncStatus: LastSyncStatus? = null
    private var fingerprint: GateFingerprint? = null

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

    override suspend fun readFingerprint(): GateFingerprint? = fingerprint

    override suspend fun saveFingerprint(fingerprint: GateFingerprint) {
        this.fingerprint = fingerprint
    }

    override suspend fun clearFingerprint() {
        fingerprint = null
    }
}
