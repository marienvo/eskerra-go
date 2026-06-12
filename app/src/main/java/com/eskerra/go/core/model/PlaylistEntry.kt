package com.eskerra.go.core.model

/**
 * Cross-device playlist state stored as R2 `playlist.json`.
 * Mirrors `packages/eskerra-core/src/playlist.ts`.
 *
 * [updatedAt] is Unix ms; [playbackOwnerId] holds the writing device's
 * `deviceInstanceId` on control writes; [controlRevision] is monotonic on
 * control writes. The merge/normalize contract lives in `core/playlist`.
 */
data class PlaylistEntry(
    val episodeId: String,
    val mp3Url: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAt: Long = 0L,
    val playbackOwnerId: String = "",
    val controlRevision: Long = 0L
)
