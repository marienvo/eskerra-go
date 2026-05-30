package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig

/** In-memory [WorkspaceStore] for JVM tests. */
class FakeWorkspaceStore : WorkspaceStore {
    private var config: WorkspaceConfig? = null

    override suspend fun read(): WorkspaceConfig? = config

    override suspend fun save(config: WorkspaceConfig) {
        this.config = config
    }

    override suspend fun clear() {
        config = null
    }
}
