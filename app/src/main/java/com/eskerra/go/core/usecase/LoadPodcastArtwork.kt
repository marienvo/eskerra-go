package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastArtworkRepository
import java.io.File

class LoadPodcastArtwork(
    private val repository: PodcastArtworkRepository,
    private val fetchRssXml: suspend (String) -> String?,
    private val workspaceKeyFor: (WorkspaceConfig, File) -> String
) {
    fun peek(config: WorkspaceConfig, filesDir: File, rssFeedUrl: String?): String? {
        val url = rssFeedUrl?.trim().orEmpty()
        if (url.isEmpty()) return null
        return repository.peekMemoryUri(workspaceKey(config, filesDir), url)
    }

    suspend fun resolve(
        config: WorkspaceConfig,
        filesDir: File,
        rssFeedUrl: String?,
        allowNetwork: Boolean = true
    ): String? {
        val url = rssFeedUrl?.trim().orEmpty()
        if (url.isEmpty()) return null
        val key = workspaceKey(config, filesDir)
        return repository.resolveUri(
            workspaceKey = key,
            rssFeedUrl = url,
            fetchRssXml = fetchRssXml,
            allowNetwork = allowNetwork
        )
    }

    suspend fun primeFromDisk(config: WorkspaceConfig, filesDir: File) {
        repository.loadMetadataFromDisk(workspaceKey(config, filesDir))
    }

    suspend fun primeForCatalog(
        config: WorkspaceConfig,
        filesDir: File,
        catalog: PodcastCatalog,
        allowNetwork: Boolean = true
    ) {
        val key = workspaceKey(config, filesDir)
        repository.loadMetadataFromDisk(key)
        feedUrlsForCatalog(catalog).forEach { url ->
            repository.resolveUri(
                workspaceKey = key,
                rssFeedUrl = url,
                fetchRssXml = fetchRssXml,
                allowNetwork = allowNetwork
            )
        }
    }

    internal fun feedUrlForEpisode(episode: PodcastEpisode, sectionRssFeedUrl: String?): String? =
        episode.rssFeedUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: sectionRssFeedUrl?.trim()?.takeIf { it.isNotEmpty() }

    private fun feedUrlsForCatalog(catalog: PodcastCatalog): Set<String> =
        catalog.sections.flatMap { section ->
            section.episodes.mapNotNull { episode ->
                feedUrlForEpisode(episode, section.rssFeedUrl)
            } + listOfNotNull(section.rssFeedUrl?.trim()?.takeIf { it.isNotEmpty() })
        }.toSet()

    private fun workspaceKey(config: WorkspaceConfig, filesDir: File): String =
        workspaceKeyFor(config, filesDir)
}
