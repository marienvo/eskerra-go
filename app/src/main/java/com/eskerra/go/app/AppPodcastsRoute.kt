package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
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
    syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
    loadPodcastArtwork: LoadPodcastArtwork,
    catalogSnapshotStore: PodcastCatalogSnapshotStore?,
    podcastShellBridge: PodcastShellBridge,
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
            podcastPlayerDriver = podcastPlayerDriver,
            syncPodcastVaultRefresh = syncPodcastVaultRefresh,
            loadPodcastArtwork = loadPodcastArtwork,
            catalogSnapshotStore = catalogSnapshotStore,
            onExitMiniPlayerArtworkMode = {
                podcastShellBridge.onExitMiniPlayerArtworkMode?.invoke()
            }
        )
    )
    DisposableEffect(podcastsViewModel) {
        podcastShellBridge.clearRowSelection = podcastsViewModel::clearSelection
        podcastShellBridge.refreshCatalog = podcastsViewModel::refresh
        podcastShellBridge.pausePlayback = podcastsViewModel::pausePlayback
        podcastShellBridge.resumePlayback = podcastsViewModel::resumePlayback
        podcastShellBridge.seekBy = podcastsViewModel::seekBy
        podcastShellBridge.seekTo = podcastsViewModel::seekTo
        podcastShellBridge.stopPlayback = podcastsViewModel::stopPlayback
        onDispose {
            podcastShellBridge.clearRowSelection = null
            podcastShellBridge.refreshCatalog = null
            podcastShellBridge.pausePlayback = null
            podcastShellBridge.resumePlayback = null
            podcastShellBridge.seekBy = null
            podcastShellBridge.seekTo = null
            podcastShellBridge.stopPlayback = null
        }
    }
    val podcastsState by podcastsViewModel.uiState.collectAsState()
    val refreshState by podcastsViewModel.refreshState.collectAsState()
    val playlistGeneration = playlistPollingHost?.playlistSyncGeneration
    if (playlistGeneration != null) {
        val generation by playlistGeneration.collectAsState()
        LaunchedEffect(generation) {
            podcastsViewModel.onPlaylistSyncGenerationChanged(generation)
        }
    }
    PodcastsScreen(
        state = podcastsState,
        refreshState = refreshState,
        config = currentConfig,
        filesDir = filesDir,
        loadPodcastArtwork = loadPodcastArtwork,
        onRetry = podcastsViewModel::refresh,
        onRefresh = podcastsViewModel::runVaultRefresh,
        onEpisodeClick = podcastsViewModel::onEpisodeClick,
        onEpisodeArtworkClick = podcastsViewModel::onEpisodeArtworkClick,
        onClearSelection = podcastsViewModel::clearSelection,
        onMarkSelected = podcastsViewModel::markSelectedEpisodes
    )
}
