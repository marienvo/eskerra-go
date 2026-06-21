package com.eskerra.go.core.player

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastPlayerMachineTest {

    @Test
    fun playlistHydrated_movesToPrimedWithPosition() {
        val episode = sampleEpisode()
        val next = PodcastPlayerMachine.reduce(
            PodcastPlaybackState(),
            PodcastPlayerEvent.PlaylistHydrated(
                episode = episode,
                positionMs = 15_000L,
                durationMs = 60_000L
            )
        )

        assertEquals(PodcastPlaybackPhase.PRIMED, next.phase)
        assertEquals(15_000L, next.positionMs)
        assertEquals(episode.id, next.activeEpisode?.id)
    }

    @Test
    fun playRequested_movesToLoadingWithEpisode() {
        val next = PodcastPlayerMachine.reduce(
            PodcastPlaybackState(),
            PodcastPlayerEvent.EpisodePlayRequested(sampleEpisode())
        )

        assertEquals(PodcastPlaybackPhase.LOADING, next.phase)
        assertEquals(sampleEpisode().id, next.activeEpisode?.id)
        assertTrue(next.transportBusy)
    }

    @Test
    fun nativeReadyPlaying_mapsToPlaying() {
        val state = primedState()

        val next = PodcastPlayerMachine.reduce(
            state,
            PodcastPlayerEvent.NativeStateChanged(
                nativeState = PodcastNativePlaybackState.READY,
                playWhenReady = true,
                positionMs = 2_000L,
                durationMs = 60_000L
            )
        )

        assertEquals(PodcastPlaybackPhase.PLAYING, next.phase)
        assertEquals(2_000L, next.positionMs)
    }

    @Test
    fun progressInsideLastTenSeconds_mapsPlayingToNearEndPlaying() {
        val state = primedState().copy(phase = PodcastPlaybackPhase.PLAYING)

        val next = PodcastPlayerMachine.reduce(
            state,
            PodcastPlayerEvent.ProgressChanged(positionMs = 51_000L, durationMs = 60_000L)
        )

        assertEquals(PodcastPlaybackPhase.NEAR_END_PLAYING, next.phase)
        assertTrue(next.isNearEnd)
    }

    @Test
    fun seekBackOutOfNearEnd_returnsToPlaying() {
        val state = primedState().copy(phase = PodcastPlaybackPhase.NEAR_END_PLAYING)

        val next = PodcastPlayerMachine.reduce(
            state,
            PodcastPlayerEvent.ProgressChanged(positionMs = 20_000L, durationMs = 60_000L)
        )

        assertEquals(PodcastPlaybackPhase.PLAYING, next.phase)
    }

    @Test
    fun nativeEnded_mapsToEnded() {
        val state = primedState().copy(phase = PodcastPlaybackPhase.PLAYING)

        val next = PodcastPlayerMachine.reduce(
            state,
            PodcastPlayerEvent.NativeStateChanged(
                nativeState = PodcastNativePlaybackState.ENDED,
                playWhenReady = false,
                positionMs = 60_000L,
                durationMs = 60_000L
            )
        )

        assertEquals(PodcastPlaybackPhase.ENDED, next.phase)
    }

    @Test
    fun stopRequested_clearsSessionSoMiniPlayerUnmounts() {
        val state = primedState().copy(
            phase = PodcastPlaybackPhase.PLAYING,
            positionMs = 30_000L,
            durationMs = 60_000L
        )

        val next = PodcastPlayerMachine.reduce(state, PodcastPlayerEvent.StopRequested)

        assertEquals(PodcastPlaybackPhase.IDLE, next.phase)
        assertEquals(null, next.activeEpisode)
        assertEquals(false, next.hasActiveEpisode)
    }

    @Test
    fun appClosed_mapsToStoppedWithoutLosingEpisode() {
        val state = primedState().copy(phase = PodcastPlaybackPhase.PAUSED)

        val next = PodcastPlayerMachine.reduce(state, PodcastPlayerEvent.AppClosed)

        assertEquals(PodcastPlaybackPhase.STOPPED, next.phase)
        assertEquals(sampleEpisode().id, next.activeEpisode?.id)
    }

    @Test
    fun nativeIdle_doesNotDemotePrimedHydration() {
        val state = PodcastPlaybackState(
            activeEpisode = sampleEpisode(),
            phase = PodcastPlaybackPhase.PRIMED,
            positionMs = 15_000L,
            durationMs = 60_000L
        )

        val next = PodcastPlayerMachine.reduce(
            state,
            PodcastPlayerEvent.NativeStateChanged(
                nativeState = PodcastNativePlaybackState.IDLE,
                playWhenReady = false,
                positionMs = 0L,
                durationMs = null
            )
        )

        assertEquals(PodcastPlaybackPhase.PRIMED, next.phase)
        assertEquals(15_000L, next.positionMs)
    }

    private fun primedState(): PodcastPlaybackState =
        PodcastPlaybackState(activeEpisode = sampleEpisode())

    private fun sampleEpisode() = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = "https://cdn/episode.mp3",
        isListened = false,
        mp3Url = "https://cdn/episode.mp3",
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily News",
        sourceFile = "2026 News - podcasts.md",
        title = "Episode title"
    )
}
