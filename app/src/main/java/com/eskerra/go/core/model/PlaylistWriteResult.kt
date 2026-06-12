package com.eskerra.go.core.model

/** Outcome of a playlist write, mirroring `PlaylistWriteResult` in `playlist.ts`. */
sealed interface PlaylistWriteResult {
    /** Entry was persisted to R2 (with `updatedAt` advanced). */
    data class Saved(val entry: PlaylistEntry) : PlaylistWriteResult

    /** A strictly-newer remote entry won the merge; the local write was dropped. */
    data class Superseded(val entry: PlaylistEntry) : PlaylistWriteResult

    /** No R2 configured — caller keeps in-memory playback only. */
    data object Skipped : PlaylistWriteResult
}
