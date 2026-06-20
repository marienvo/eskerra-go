package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection

sealed interface PodcastsUiState {
    data object Loading : PodcastsUiState

    data class Content(
        val sections: List<PodcastSection>,
        val playerState: PodcastPlaybackState = PodcastPlaybackState()
    ) : PodcastsUiState

    data object Empty : PodcastsUiState

    data class Error(val message: String) : PodcastsUiState
}
