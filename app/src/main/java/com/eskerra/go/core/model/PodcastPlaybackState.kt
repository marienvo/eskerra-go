package com.eskerra.go.core.model

enum class PodcastPlaybackPhase {
    IDLE,
    PRIMED,
    LOADING,
    PLAYING,
    PAUSED,
    NEAR_END_PLAYING,
    NEAR_END_PAUSED,
    ENDED,
    STOPPED,
    ERROR
}

data class PodcastPlaybackState(
    val activeEpisode: PodcastEpisode? = null,
    val phase: PodcastPlaybackPhase = PodcastPlaybackPhase.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val transportBusy: Boolean = false,
    val errorMessage: String? = null
) {
    val hasActiveEpisode: Boolean get() = activeEpisode != null

    val isPlaying: Boolean get() =
        phase == PodcastPlaybackPhase.PLAYING || phase == PodcastPlaybackPhase.NEAR_END_PLAYING

    val isPaused: Boolean get() =
        phase == PodcastPlaybackPhase.PAUSED || phase == PodcastPlaybackPhase.NEAR_END_PAUSED

    val isNearEnd: Boolean get() =
        phase == PodcastPlaybackPhase.NEAR_END_PLAYING ||
            phase == PodcastPlaybackPhase.NEAR_END_PAUSED

    /** True while playback is in-flight and another episode must not be started. */
    val locksEpisodeSwitch: Boolean get() =
        phase == PodcastPlaybackPhase.LOADING ||
            phase == PodcastPlaybackPhase.PLAYING ||
            phase == PodcastPlaybackPhase.NEAR_END_PLAYING

    fun isActiveEpisode(episode: PodcastEpisode): Boolean = activeEpisode?.id == episode.id
}
