package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.feature.podcasts.PlaylistR2PollingHost
import com.eskerra.go.feature.podcasts.PodcastsScreen
import com.eskerra.go.feature.podcasts.PodcastsViewModel
import java.io.File

@Composable
internal fun AppPodcastsRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadPodcastCatalog: LoadPodcastCatalog,
    markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    podcastPlaylistSync: PodcastPlaylistSync,
    podcastPlayerDriver: PodcastPlayerDriver,
    playlistPollingHost: PlaylistR2PollingHost?
) {
    val podcastsViewModel: PodcastsViewModel = viewModel(
        key = "${currentConfig.remoteUri.orEmpty()}:${currentConfig.branch}",
        factory = PodcastsViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadPodcastCatalog = loadPodcastCatalog,
            markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
            podcastPlaylistSync = podcastPlaylistSync,
            podcastPlayerDriver = podcastPlayerDriver
        )
    )
    val podcastsState by podcastsViewModel.uiState.collectAsState()
    val playlistGeneration = playlistPollingHost?.playlistSyncGeneration
    if (playlistGeneration != null) {
        val generation by playlistGeneration.collectAsState()
        LaunchedEffect(generation) {
            podcastsViewModel.onPlaylistSyncGenerationChanged(generation)
        }
    }
    PodcastsScreen(
        state = podcastsState,
        onRetry = podcastsViewModel::refresh,
        onEpisodeClick = podcastsViewModel::onEpisodeClick,
        onPausePlayback = podcastsViewModel::pausePlayback,
        onResumePlayback = podcastsViewModel::resumePlayback,
        onStopPlayback = podcastsViewModel::stopPlayback,
        onSeekBy = podcastsViewModel::seekBy
    )
}
