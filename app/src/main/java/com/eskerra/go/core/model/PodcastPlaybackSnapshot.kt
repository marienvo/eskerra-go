package com.eskerra.go.core.model

/** Local resume snapshot for podcast playback. Per-device only; never synced via git. */
data class PodcastPlaybackSnapshot(
    val episodeId: String,
    val mp3Url: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAtMs: Long
) {
    fun toPlaylistEntry(): PlaylistEntry = PlaylistEntry(
        episodeId = episodeId,
        mp3Url = mp3Url,
        positionMs = positionMs,
        durationMs = durationMs
    )
}
