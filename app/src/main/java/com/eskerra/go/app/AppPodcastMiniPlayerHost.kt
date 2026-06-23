package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.feature.podcasts.PodcastMiniPlayer
import com.eskerra.go.feature.podcasts.PodcastsActionError
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun AppPodcastMiniPlayerHost(
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    podcastPlayerDriver: PodcastPlayerDriver,
    bridge: PodcastShellBridge
) {
    val playerState by podcastPlayerDriver.state.collectAsState()
    val scope = rememberCoroutineScope()
    var artworkSelectionMode by remember { mutableStateOf(false) }
    var markInFlight by remember { mutableStateOf(false) }
    var markError by remember { mutableStateOf<PodcastsActionError?>(null) }

    SideEffect {
        bridge.onExitMiniPlayerArtworkMode = { artworkSelectionMode = false }
    }

    if (!playerState.hasActiveEpisode) return

    PodcastMiniPlayer(
        playerState = playerState,
        config = config,
        filesDir = filesDir,
        loadPodcastArtwork = loadPodcastArtwork,
        artworkSelectionMode = artworkSelectionMode,
        onArtworkSelectionToggle = {
            artworkSelectionMode = !artworkSelectionMode
            if (artworkSelectionMode) {
                bridge.clearRowSelection?.invoke()
            }
        },
        onArchiveActiveEpisode = {
            val episode = playerState.activeEpisode ?: return@PodcastMiniPlayer
            scope.launch {
                markInFlight = true
                markError = null
                markPodcastEpisodesPlayed(config, filesDir, listOf(episode))
                    .onSuccess { result ->
                        if (result.updated) {
                            bridge.stopPlayback?.invoke()
                            bridge.refreshCatalog?.invoke()
                        }
                        artworkSelectionMode = false
                    }
                    .onFailure {
                        markError = PodcastsActionError.MarkSelectedFailed
                    }
                markInFlight = false
            }
        },
        onDismissActiveEpisode = {
            artworkSelectionMode = false
            bridge.stopPlayback?.invoke()
        },
        onPausePlayback = { bridge.pausePlayback?.invoke() },
        onResumePlayback = { bridge.resumePlayback?.invoke() },
        onSeekBy = { delta -> bridge.seekBy?.invoke(delta) },
        onSeekTo = { position -> bridge.seekTo?.invoke(position) },
        markInFlight = markInFlight,
        markError = markError
    )
}
