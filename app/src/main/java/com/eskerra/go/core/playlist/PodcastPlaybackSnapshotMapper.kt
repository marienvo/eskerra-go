package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackSnapshot
import com.eskerra.go.core.model.PodcastPlaybackState

fun EskerraLocalSettings.playbackSnapshot(): PodcastPlaybackSnapshot? {
    val episodeId = podcastEpisodeId ?: return null
    val mp3Url = podcastMp3Url ?: return null
    val positionMs = podcastPositionMs ?: return null
    val updatedAtMs = podcastSnapshotUpdatedAtMs ?: return null
    return PodcastPlaybackSnapshot(
        episodeId = episodeId,
        mp3Url = mp3Url,
        positionMs = positionMs,
        durationMs = podcastDurationMs,
        updatedAtMs = updatedAtMs
    )
}

fun EskerraLocalSettings.withPlaybackSnapshot(
    snapshot: PodcastPlaybackSnapshot?
): EskerraLocalSettings = if (snapshot == null) {
    copy(
        podcastEpisodeId = null,
        podcastMp3Url = null,
        podcastPositionMs = null,
        podcastDurationMs = null,
        podcastSnapshotUpdatedAtMs = null
    )
} else {
    copy(
        podcastEpisodeId = snapshot.episodeId,
        podcastMp3Url = snapshot.mp3Url,
        podcastPositionMs = snapshot.positionMs,
        podcastDurationMs = snapshot.durationMs,
        podcastSnapshotUpdatedAtMs = snapshot.updatedAtMs
    )
}

fun PodcastPlaybackState.toPersistedSnapshot(
    clock: () -> Long = System::currentTimeMillis
): PodcastPlaybackSnapshot? {
    val episode = activeEpisode ?: return null
    if (phase == PodcastPlaybackPhase.IDLE) return null
    if (positionMs < RESUMABLE_PODCAST_MIN_PROGRESS_MS && !isPlaying) return null
    return PodcastPlaybackSnapshot(
        episodeId = episode.id,
        mp3Url = episode.mp3Url,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtMs = clock()
    )
}
