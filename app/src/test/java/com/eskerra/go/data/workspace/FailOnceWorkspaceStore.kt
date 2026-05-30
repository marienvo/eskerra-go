package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig

/** [WorkspaceStore] that throws on the first save, then delegates to [FakeWorkspaceStore]. */
class FailOnceWorkspaceStore : WorkspaceStore {
    private val delegate = FakeWorkspaceStore()
    private var shouldFailSave = true

    override suspend fun read(): WorkspaceConfig? = delegate.read()

    override suspend fun save(config: WorkspaceConfig) {
        if (shouldFailSave) {
            shouldFailSave = false
            throw RuntimeException("metadata save failed")
        }
        delegate.save(config)
    }

    override suspend fun clear() {
        delegate.clear()
    }
}
