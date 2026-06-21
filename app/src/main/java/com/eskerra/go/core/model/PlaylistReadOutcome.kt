package com.eskerra.go.core.model

/**
 * Result of reading the R2 `playlist.json`, distinguishing a *confirmed* empty remote from a
 * read that could not be completed. The legacy [PlaylistSyncRepository.readPlaylist] collapses
 * both into `null`, which makes a transient network failure indistinguishable from "the playlist
 * was cleared elsewhere" — a conflation that wrongly tears down a resumable local session.
 */
sealed interface PlaylistReadOutcome {
    /** R2 returned a playlist object. */
    data class Present(val entry: PlaylistEntry) : PlaylistReadOutcome

    /** R2 was reachable and confirmed there is no playlist object (deliberately cleared/absent). */
    data object Empty : PlaylistReadOutcome

    /**
     * The read could not be completed — R2 is not configured or the request failed transiently.
     * Callers must preserve local playback state: absence here is unknown, not "cleared".
     */
    data object Unavailable : PlaylistReadOutcome
}
