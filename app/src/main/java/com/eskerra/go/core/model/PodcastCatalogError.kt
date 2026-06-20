package com.eskerra.go.core.model

sealed interface PodcastCatalogError {
    data object InvalidWorkspacePath : PodcastCatalogError
    data object WorkspaceMissing : PodcastCatalogError
    data class LoadFailed(val message: String?) : PodcastCatalogError
}

class PodcastCatalogException(val error: PodcastCatalogError) : Exception()
