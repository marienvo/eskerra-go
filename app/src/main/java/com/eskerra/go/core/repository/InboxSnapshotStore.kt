package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Persists the last inbox list snapshot for stale-while-revalidate cold start. */
interface InboxSnapshotStore {
    suspend fun read(config: WorkspaceConfig, filesDir: File): List<NoteSummary>?

    suspend fun save(config: WorkspaceConfig, filesDir: File, summaries: List<NoteSummary>)

    suspend fun clear(config: WorkspaceConfig, filesDir: File)

    suspend fun clearAll(filesDir: File)
}
