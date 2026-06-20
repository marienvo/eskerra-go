package com.eskerra.go.core.player

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import kotlin.math.max

enum class PodcastNativePlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR
}

sealed interface PodcastPlayerEvent {
    data class PlaylistHydrated(
        val episode: PodcastEpisode,
        val positionMs: Long,
        val durationMs: Long?
    ) : PodcastPlayerEvent

    data class EpisodePlayRequested(val episode: PodcastEpisode) : PodcastPlayerEvent
    data object PauseRequested : PodcastPlayerEvent
    data object ResumeRequested : PodcastPlayerEvent
    data object StopRequested : PodcastPlayerEvent
    data object AppClosed : PodcastPlayerEvent

    data class NativeStateChanged(
        val nativeState: PodcastNativePlaybackState,
        val playWhenReady: Boolean,
        val positionMs: Long,
        val durationMs: Long?
    ) : PodcastPlayerEvent

    data class ProgressChanged(val positionMs: Long, val durationMs: Long?) : PodcastPlayerEvent
    data class PlaybackError(val message: String?) : PodcastPlayerEvent
}

object PodcastPlayerMachine {
    const val NEAR_END_REMAINING_MS = 10_000L

    fun reduce(state: PodcastPlaybackState, event: PodcastPlayerEvent): PodcastPlaybackState =
        when (event) {
            is PodcastPlayerEvent.PlaylistHydrated -> PodcastPlaybackState(
                activeEpisode = event.episode,
                phase = PodcastPlaybackPhase.PRIMED,
                positionMs = sanitizedPosition(event.positionMs),
                durationMs = event.durationMs?.takeIf { it > 0L },
                transportBusy = false
            )

            is PodcastPlayerEvent.EpisodePlayRequested -> PodcastPlaybackState(
                activeEpisode = event.episode,
                phase = PodcastPlaybackPhase.LOADING,
                transportBusy = true
            )

            PodcastPlayerEvent.PauseRequested -> state.copy(
                phase = pausedPhase(state),
                transportBusy = true,
                errorMessage = null
            )

            PodcastPlayerEvent.ResumeRequested -> state.copy(
                phase = PodcastPlaybackPhase.LOADING,
                transportBusy = true,
                errorMessage = null
            )

            PodcastPlayerEvent.StopRequested -> state.copy(
                phase = PodcastPlaybackPhase.STOPPED,
                transportBusy = false,
                positionMs = 0L,
                errorMessage = null
            )

            PodcastPlayerEvent.AppClosed -> state.copy(
                phase = PodcastPlaybackPhase.STOPPED,
                transportBusy = false,
                errorMessage = null
            )

            is PodcastPlayerEvent.NativeStateChanged -> {
                val nativeIdleWithoutLoadedMedia =
                    event.nativeState == PodcastNativePlaybackState.IDLE &&
                        event.positionMs == 0L &&
                        state.hasActiveEpisode &&
                        state.positionMs > 0L
                if (
                    nativeIdleWithoutLoadedMedia &&
                    (
                        state.phase == PodcastPlaybackPhase.PRIMED ||
                            state.phase == PodcastPlaybackPhase.PAUSED ||
                            state.phase == PodcastPlaybackPhase.NEAR_END_PAUSED
                        )
                ) {
                    state
                } else {
                    state.copy(
                        phase = phaseFromNative(
                            nativeState = event.nativeState,
                            playWhenReady = event.playWhenReady,
                            positionMs = event.positionMs,
                            durationMs = event.durationMs,
                            hasActiveEpisode = state.activeEpisode != null
                        ),
                        positionMs = if (nativeIdleWithoutLoadedMedia) {
                            state.positionMs
                        } else {
                            sanitizedPosition(event.positionMs)
                        },
                        durationMs = event.durationMs?.takeIf { it > 0L } ?: state.durationMs,
                        transportBusy = event.nativeState == PodcastNativePlaybackState.BUFFERING,
                        errorMessage = if (event.nativeState == PodcastNativePlaybackState.ERROR) {
                            state.errorMessage
                        } else {
                            null
                        }
                    )
                }
            }

            is PodcastPlayerEvent.ProgressChanged -> {
                val positionMs = sanitizedPosition(event.positionMs)
                val durationMs = event.durationMs?.takeIf { it > 0L }
                state.copy(
                    phase = progressPhase(state.phase, positionMs, durationMs),
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            }

            is PodcastPlayerEvent.PlaybackError -> state.copy(
                phase = PodcastPlaybackPhase.ERROR,
                transportBusy = false,
                errorMessage = event.message ?: "Playback failed."
            )
        }

    private fun phaseFromNative(
        nativeState: PodcastNativePlaybackState,
        playWhenReady: Boolean,
        positionMs: Long,
        durationMs: Long?,
        hasActiveEpisode: Boolean
    ): PodcastPlaybackPhase = when (nativeState) {
        PodcastNativePlaybackState.IDLE ->
            if (hasActiveEpisode) PodcastPlaybackPhase.STOPPED else PodcastPlaybackPhase.IDLE
        PodcastNativePlaybackState.BUFFERING -> PodcastPlaybackPhase.LOADING
        PodcastNativePlaybackState.READY -> {
            val nearEnd = isNearEnd(positionMs, durationMs)
            when {
                playWhenReady && nearEnd -> PodcastPlaybackPhase.NEAR_END_PLAYING
                playWhenReady -> PodcastPlaybackPhase.PLAYING
                nearEnd -> PodcastPlaybackPhase.NEAR_END_PAUSED
                else -> PodcastPlaybackPhase.PAUSED
            }
        }
        PodcastNativePlaybackState.ENDED -> PodcastPlaybackPhase.ENDED
        PodcastNativePlaybackState.ERROR -> PodcastPlaybackPhase.ERROR
    }

    private fun progressPhase(
        current: PodcastPlaybackPhase,
        positionMs: Long,
        durationMs: Long?
    ): PodcastPlaybackPhase {
        val nearEnd = isNearEnd(positionMs, durationMs)
        return when (current) {
            PodcastPlaybackPhase.PLAYING,
            PodcastPlaybackPhase.NEAR_END_PLAYING ->
                if (nearEnd) PodcastPlaybackPhase.NEAR_END_PLAYING else PodcastPlaybackPhase.PLAYING

            PodcastPlaybackPhase.PAUSED,
            PodcastPlaybackPhase.NEAR_END_PAUSED ->
                if (nearEnd) PodcastPlaybackPhase.NEAR_END_PAUSED else PodcastPlaybackPhase.PAUSED

            else -> current
        }
    }

    private fun pausedPhase(state: PodcastPlaybackState): PodcastPlaybackPhase =
        if (state.isNearEnd) PodcastPlaybackPhase.NEAR_END_PAUSED else PodcastPlaybackPhase.PAUSED

    private fun isNearEnd(positionMs: Long, durationMs: Long?): Boolean {
        val duration = durationMs ?: return false
        if (duration <= NEAR_END_REMAINING_MS) return false
        val remaining = duration - max(0L, positionMs)
        return remaining in 0L..NEAR_END_REMAINING_MS
    }

    private fun sanitizedPosition(positionMs: Long): Long = max(0L, positionMs)
}
