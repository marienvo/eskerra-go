package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection

sealed interface PodcastsUiState {
    data object Loading : PodcastsUiState

    data class Content(
        val sections: List<PodcastSection>,
        val playerState: PodcastPlaybackState = PodcastPlaybackState(),
        val selectedEpisodeIds: Set<String> = emptySet(),
        val markInFlight: Boolean = false,
        val markError: String? = null
    ) : PodcastsUiState

    data object Empty : PodcastsUiState

    data class Error(val message: String) : PodcastsUiState
}

/**
 * Vault-refresh feedback rendered as the 3px header strip (spec §7.4). [active]
 * spans the whole serialized run; [percent] drives a determinate fill when the
 * native RSS sync reports progress, otherwise the strip sweeps indeterminately.
 */
data class PodcastRefreshState(
    val active: Boolean = false,
    val percent: Int? = null,
    val error: String? = null
)

sealed interface PodcastsUiEvent {
    data object PauseToSwitchEpisode : PodcastsUiEvent
}
