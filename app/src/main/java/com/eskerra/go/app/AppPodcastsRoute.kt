package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.feature.podcasts.PodcastsScreen
import com.eskerra.go.feature.podcasts.PodcastsViewModel
import java.io.File

@Composable
internal fun AppPodcastsRoute(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadPodcastCatalog: LoadPodcastCatalog
) {
    val podcastsViewModel: PodcastsViewModel = viewModel(
        key = "${currentConfig.remoteUri.orEmpty()}:${currentConfig.branch}",
        factory = PodcastsViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadPodcastCatalog = loadPodcastCatalog
        )
    )
    val podcastsState by podcastsViewModel.uiState.collectAsState()
    PodcastsScreen(
        state = podcastsState,
        onRetry = podcastsViewModel::refresh,
        onEpisodeClick = podcastsViewModel::onEpisodeClick
    )
}
