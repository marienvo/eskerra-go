package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastNativeSessionSnapshot
import com.eskerra.go.core.model.PodcastPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PodcastPlayerDriver {
    val state: StateFlow<PodcastPlaybackState>

    fun play(episode: PodcastEpisode, startPositionMs: Long = 0L)

    /**
     * Supplies artwork URI resolvers for media-session metadata. [peekArtworkUri] must be cheap
     * and synchronous; [resolveArtworkUri] may touch disk/network and can update the active media
     * item after playback has already started.
     */
    fun configureArtworkResolver(
        peekArtworkUri: (PodcastEpisode) -> String?,
        resolveArtworkUri: suspend (PodcastEpisode) -> String?
    ) {}

    fun hydrate(episode: PodcastEpisode, positionMs: Long, durationMs: Long?)

    fun pause()

    fun resume()

    fun stop()

    fun seekBy(deltaMs: Long)

    fun seekTo(positionMs: Long)

    fun currentNativeSession(): PodcastNativeSessionSnapshot?

    /**
     * Suspends until the underlying media session connection has settled (or a bounded timeout
     * elapses). Call before [currentNativeSession] on app launch so restore reads the live
     * session instead of racing an async connect and seeing none. No-op for drivers that connect
     * synchronously.
     */
    suspend fun awaitConnection() {}

    /**
     * Reflects the live native session ([snapshot]) into [state] for [episode] without issuing any
     * transport command. Used on launch/reconnect so the in-app player shows what is actually
     * playing (play/pause + live position), rather than a possibly-stale persisted snapshot.
     */
    fun adoptNativeSession(episode: PodcastEpisode, snapshot: PodcastNativeSessionSnapshot) {}

    fun release()
}
