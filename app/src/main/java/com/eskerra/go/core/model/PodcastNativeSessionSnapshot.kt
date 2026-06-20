package com.eskerra.go.core.model

/** Read-only snapshot of the Android Media3 session, when a media item is loaded. */
data class PodcastNativeSessionSnapshot(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val isPlaying: Boolean
)
