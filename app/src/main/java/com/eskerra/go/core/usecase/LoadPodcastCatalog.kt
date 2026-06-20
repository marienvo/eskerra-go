package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogRepository
import java.io.File

class LoadPodcastCatalog(private val repository: PodcastCatalogRepository) {
    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
        repository.load(config, filesDir)
}
