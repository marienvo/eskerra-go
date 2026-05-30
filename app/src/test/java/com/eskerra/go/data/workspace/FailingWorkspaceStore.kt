package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig

/** [WorkspaceStore] that always fails on save for error-path tests. */
class FailingWorkspaceStore : WorkspaceStore {
    var readResult: WorkspaceConfig? = null
    var saveFailure: Exception = RuntimeException("metadata save failed")

    override suspend fun read(): WorkspaceConfig? = readResult

    override suspend fun save(config: WorkspaceConfig): Unit = throw saveFailure

    override suspend fun clear() {
        readResult = null
    }
}
