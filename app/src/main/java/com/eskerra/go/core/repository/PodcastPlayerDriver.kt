package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PodcastPlayerDriver {
    val state: StateFlow<PodcastPlaybackState>

    fun play(episode: PodcastEpisode, startPositionMs: Long = 0L)

    fun hydrate(episode: PodcastEpisode, positionMs: Long, durationMs: Long?)

    fun pause()

    fun resume()

    fun stop()

    fun seekBy(deltaMs: Long)

    fun seekTo(positionMs: Long)

    fun release()
}
