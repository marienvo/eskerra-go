package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Loads parsed podcast stub files from the workspace vault. */
interface PodcastCatalogRepository {
    suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog>
}
