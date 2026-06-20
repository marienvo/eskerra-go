package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode

data class PodcastPlaylistHydration(val episode: PodcastEpisode, val positionMs: Long)

fun findEpisodeForPlaylistEntry(catalog: PodcastCatalog, entry: PlaylistEntry): PodcastEpisode? {
    catalog.allEpisodes.firstOrNull { it.id == entry.episodeId && it.mp3Url == entry.mp3Url }
        ?.let { return it }
    return catalog.allEpisodes.firstOrNull { it.id == entry.episodeId }
}

fun resolvePodcastPlaylistHydration(
    catalog: PodcastCatalog,
    entry: PlaylistEntry
): PodcastPlaylistHydration? {
    val episode = findEpisodeForPlaylistEntry(catalog, entry) ?: return null
    if (episode.isListened) return null
    return PodcastPlaylistHydration(
        episode = episode,
        positionMs = entry.positionMs.coerceAtLeast(0L)
    )
}

fun shouldClearPlaylistForCatalog(catalog: PodcastCatalog, entry: PlaylistEntry): Boolean {
    val episode = findEpisodeForPlaylistEntry(catalog, entry) ?: return true
    return episode.isListened
}
