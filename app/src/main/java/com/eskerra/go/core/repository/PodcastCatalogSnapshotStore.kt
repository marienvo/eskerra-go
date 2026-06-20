package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** File-backed Phase-1 podcast catalog snapshot for warm-start preload (spec §6.7). */
interface PodcastCatalogSnapshotStore {
    suspend fun read(config: WorkspaceConfig, filesDir: File): PodcastCatalog?

    suspend fun save(config: WorkspaceConfig, filesDir: File, catalog: PodcastCatalog)

    suspend fun clear(config: WorkspaceConfig, filesDir: File)
}
