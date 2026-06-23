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

    data class EpisodePlayRequested(val episode: PodcastEpisode, val startPositionMs: Long = 0L) :
        PodcastPlayerEvent
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
                // Carry the resume position so LOADING ("Resuming…") shows where playback will
                // continue instead of flashing 0%. Keep the duration when re-playing the same
                // episode so the progress bar denominator is stable across the resume.
                positionMs = sanitizedPosition(event.startPositionMs),
                durationMs = if (state.activeEpisode?.id == event.episode.id) {
                    state.durationMs
                } else {
                    null
                },
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

            // Explicit stop/dismiss tears the session down completely so the global mini player
            // unmounts (hasActiveEpisode == false). AppClosed below keeps the episode for restore.
            PodcastPlayerEvent.StopRequested -> PodcastPlaybackState()

            PodcastPlayerEvent.AppClosed -> state.copy(
                phase = PodcastPlaybackPhase.STOPPED,
                transportBusy = false,
                errorMessage = null
            )

            is PodcastPlayerEvent.NativeStateChanged -> {
                val transientZeroPosition = isTransientZeroPosition(
                    reportedPositionMs = event.positionMs,
                    state = state,
                    isEnded = event.nativeState == PodcastNativePlaybackState.ENDED
                )
                val nativeIdleWithoutLoadedMedia =
                    transientZeroPosition &&
                        event.nativeState == PodcastNativePlaybackState.IDLE &&
                        state.phase != PodcastPlaybackPhase.LOADING
                if (nativeIdleWithoutLoadedMedia) {
                    state
                } else {
                    state.copy(
                        phase = phaseFromNative(
                            nativeState = event.nativeState,
                            playWhenReady = event.playWhenReady,
                            positionMs = if (transientZeroPosition) {
                                state.positionMs
                            } else {
                                event.positionMs
                            },
                            durationMs = event.durationMs,
                            hasActiveEpisode = state.activeEpisode != null
                        ),
                        positionMs = if (transientZeroPosition) {
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
                // The progress ticker keeps running for a primed/paused session whose native media
                // item is not loaded yet (e.g. after launch restore), reporting position 0. Ignore
                // it so it can't reset the restored resume point (and null out the duration).
                val transientZeroPosition = isTransientZeroPosition(event.positionMs, state)
                if (transientZeroPosition) {
                    state
                } else {
                    val positionMs = sanitizedPosition(event.positionMs)
                    // Keep the known duration when a tick reports it as unknown (C.TIME_UNSET)
                    // while the item is (re)loading on resume — an episode's length doesn't
                    // change, and dropping it to null flashes the progress bar to 0%.
                    val durationMs = event.durationMs?.takeIf { it > 0L } ?: state.durationMs
                    state.copy(
                        phase = progressPhase(state.phase, positionMs, durationMs),
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                }
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

    // A zero position reported while we already hold a known position is a transient artifact of
    // (re)loading a media item before a pending seek lands. Never let it reset a primed/paused/
    // loading session — that is what flashes 0% on resume. ENDED is excluded: position 0 there
    // is real (the stream finished). isEnded is always false for ProgressChanged callers since
    // the ticker doesn't run for ended items.
    private fun isTransientZeroPosition(
        reportedPositionMs: Long,
        state: PodcastPlaybackState,
        isEnded: Boolean = false
    ): Boolean = reportedPositionMs == 0L &&
        state.positionMs > 0L &&
        state.hasActiveEpisode &&
        !isEnded &&
        state.phase in RESUMABLE_PHASES

    private val RESUMABLE_PHASES = setOf(
        PodcastPlaybackPhase.PRIMED,
        PodcastPlaybackPhase.LOADING,
        PodcastPlaybackPhase.PAUSED,
        PodcastPlaybackPhase.NEAR_END_PAUSED
    )

    private fun sanitizedPosition(positionMs: Long): Long = max(0L, positionMs)
}
