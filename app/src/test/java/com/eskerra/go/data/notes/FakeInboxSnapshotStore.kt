package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnapshotStore
import java.io.File

/** In-memory [InboxSnapshotStore] for JVM tests. */
class FakeInboxSnapshotStore : InboxSnapshotStore {
    private var summaries: List<NoteSummary>? = null

    override suspend fun read(config: WorkspaceConfig, filesDir: File): List<NoteSummary>? =
        summaries

    override suspend fun save(
        config: WorkspaceConfig,
        filesDir: File,
        summaries: List<NoteSummary>
    ) {
        this.summaries = summaries
    }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        summaries = null
    }

    override suspend fun clearAll(filesDir: File) {
        summaries = null
    }
}
