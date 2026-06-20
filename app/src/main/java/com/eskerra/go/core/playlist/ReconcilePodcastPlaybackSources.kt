package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastNativeSessionSnapshot
import com.eskerra.go.core.model.PodcastPlaybackSnapshot

const val RESUMABLE_PODCAST_MIN_PROGRESS_MS = 10_000L

/**
 * Picks the best resume source for podcast playback.
 * Priority: active native session, local snapshot, remote playlist entry.
 */
fun reconcilePodcastPlaybackSources(
    catalog: PodcastCatalog?,
    localSnapshot: PodcastPlaybackSnapshot?,
    remoteEntry: PlaylistEntry?,
    nativeSession: PodcastNativeSessionSnapshot?
): PodcastPlaylistHydration? {
    if (catalog == null) return null

    nativeSession?.let { native ->
        hydrationFromNativeSession(catalog, native)?.let { return it }
    }

    localSnapshot?.let { snapshot ->
        resolvePodcastPlaylistHydration(catalog, snapshot.toPlaylistEntry())?.let { return it }
    }

    remoteEntry?.let { entry ->
        resolvePodcastPlaylistHydration(catalog, entry)?.let { return it }
    }

    return null
}

fun hasResumablePodcastPlayback(
    catalog: PodcastCatalog?,
    localSnapshot: PodcastPlaybackSnapshot?,
    remoteEntry: PlaylistEntry?,
    nativeSession: PodcastNativeSessionSnapshot?
): Boolean = reconcilePodcastPlaybackSources(
    catalog = catalog,
    localSnapshot = localSnapshot,
    remoteEntry = remoteEntry,
    nativeSession = nativeSession
) != null

private fun hydrationFromNativeSession(
    catalog: PodcastCatalog,
    native: PodcastNativeSessionSnapshot
): PodcastPlaylistHydration? {
    if (native.episodeId.isBlank()) return null
    val episode = catalog.allEpisodes.firstOrNull { it.id == native.episodeId } ?: return null
    if (episode.isListened) return null
    val hasMeaningfulProgress =
        native.isPlaying || native.positionMs >= RESUMABLE_PODCAST_MIN_PROGRESS_MS
    if (!hasMeaningfulProgress) return null
    return PodcastPlaylistHydration(
        episode = episode,
        positionMs = native.positionMs.coerceAtLeast(0L)
    )
}
