package com.eskerra.go.core.repository

/**
 * RSS-keyed podcast artwork cache (spec §12). Memory is authoritative in-session;
 * disk metadata + image bytes survive restarts under `filesDir/podcast-artwork-files/`.
 */
interface PodcastArtworkRepository {
    fun peekMemoryUri(workspaceKey: String, rssFeedUrl: String): String?

    suspend fun loadMetadataFromDisk(workspaceKey: String)

    suspend fun resolveUri(
        workspaceKey: String,
        rssFeedUrl: String,
        fetchRssXml: suspend (String) -> String?,
        allowNetwork: Boolean
    ): String?
}
