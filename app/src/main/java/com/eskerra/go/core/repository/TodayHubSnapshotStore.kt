package com.eskerra.go.core.repository

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import java.io.File

/** Persists the last current-week Today Hub state for stale-while-revalidate cold start. */
interface TodayHubSnapshotStore {
    suspend fun read(config: WorkspaceConfig, filesDir: File): TodayHubSnapshot?

    suspend fun save(config: WorkspaceConfig, filesDir: File, snapshot: TodayHubSnapshot)

    suspend fun clear(config: WorkspaceConfig, filesDir: File)
}
