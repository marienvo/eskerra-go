package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.playlist.toPersistedSnapshot
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.feature.podcasts.PlaylistR2PollingHost
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun AppPodcastBootstrap(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    workspaceRoot: File?,
    currentRoute: String?,
    navController: NavHostController,
    podcastPlayerDriver: PodcastPlayerDriver,
    podcastShellStateWiring: PodcastShellStateWiring,
    podcastPlaylistSync: PodcastPlaylistSync,
    loadPodcastArtwork: LoadPodcastArtwork,
    playlistPollingHost: PlaylistR2PollingHost?,
    bridge: PodcastShellBridge,
    currentDestination: NavDestination?,
    onPodcastFirstLaunchChanged: (Boolean) -> Unit
) {
    AppPodcastPlaylistEffects(
        pollingHost = playlistPollingHost,
        podcastPlayerDriver = podcastPlayerDriver
    )
    val scope = rememberCoroutineScope()
    var initialNavigationDone by remember(currentConfig) { mutableStateOf(false) }
    val playerState by podcastPlayerDriver.state.collectAsState()

    LaunchedEffect(currentConfig, filesDir) {
        loadPodcastArtwork.primeFromDisk(currentConfig, filesDir)
    }

    LaunchedEffect(currentConfig, workspaceRoot) {
        val restore = podcastShellStateWiring.restorePodcastPlayback(
            currentConfig,
            filesDir,
            workspaceRoot
        )
        val initialRoute = resolveInitialShellRoute(
            preferredShellMode = restore.preferredShellMode,
            hasResumablePlayback = restore.hydrated
        )
        onPodcastFirstLaunchChanged(shouldDismissSplashWithoutInbox(initialRoute))
        if (!initialNavigationDone && currentRoute != initialRoute) {
            navController.navigateTab(currentRoute, initialRoute) {}
            initialNavigationDone = true
        } else {
            initialNavigationDone = true
        }
    }

    LaunchedEffect(currentDestination) {
        shellModeForDestination(currentDestination)?.let {
            podcastShellStateWiring.persistAppShellMode(it)
        }
    }

    LaunchedEffect(playerState) {
        val snapshot = playerState.toPersistedSnapshot()
        if (snapshot != null) {
            podcastShellStateWiring.persistPodcastPlaybackSnapshot(snapshot)
        } else if (
            !playerState.hasActiveEpisode ||
            playerState.phase == PodcastPlaybackPhase.STOPPED
        ) {
            podcastShellStateWiring.clearPodcastPlaybackSnapshot()
        }
    }

    DisposableEffect(scope, workspaceRoot, bridge, podcastShellStateWiring) {
        bridge.pausePlayback = {
            scope.launch {
                podcastPlayerDriver.pause()
                persistSnapshotAfterUserAction(
                    podcastPlayerDriver,
                    podcastShellStateWiring,
                    podcastPlaylistSync,
                    workspaceRoot
                )
            }
        }
        bridge.resumePlayback = {
            scope.launch {
                val state = podcastPlayerDriver.state.value
                val episode = state.activeEpisode ?: return@launch
                if (state.isPlaying) return@launch
                when (state.phase) {
                    PodcastPlaybackPhase.PRIMED,
                    PodcastPlaybackPhase.PAUSED,
                    PodcastPlaybackPhase.NEAR_END_PAUSED,
                    PodcastPlaybackPhase.STOPPED ->
                        podcastPlayerDriver.play(episode, state.positionMs)
                    else -> podcastPlayerDriver.resume()
                }
                persistSnapshotAfterUserAction(
                    podcastPlayerDriver,
                    podcastShellStateWiring,
                    podcastPlaylistSync,
                    workspaceRoot
                )
            }
        }
        bridge.seekBy = { delta ->
            scope.launch {
                podcastPlayerDriver.seekBy(delta)
                persistSnapshotAfterUserAction(
                    podcastPlayerDriver,
                    podcastShellStateWiring,
                    podcastPlaylistSync,
                    workspaceRoot
                )
            }
        }
        bridge.seekTo = { position ->
            scope.launch {
                podcastPlayerDriver.seekTo(position)
                persistSnapshotAfterUserAction(
                    podcastPlayerDriver,
                    podcastShellStateWiring,
                    podcastPlaylistSync,
                    workspaceRoot
                )
            }
        }
        bridge.stopPlayback = {
            scope.launch {
                podcastPlayerDriver.stop()
                podcastShellStateWiring.clearPodcastPlaybackSnapshot()
                workspaceRoot?.let { podcastPlaylistSync.clear(it) }
            }
        }
        onDispose {
            bridge.pausePlayback = null
            bridge.resumePlayback = null
            bridge.seekBy = null
            bridge.seekTo = null
            bridge.stopPlayback = null
        }
    }
}

private suspend fun persistSnapshotAfterUserAction(
    podcastPlayerDriver: PodcastPlayerDriver,
    podcastShellStateWiring: PodcastShellStateWiring,
    podcastPlaylistSync: PodcastPlaylistSync,
    workspaceRoot: File?
) {
    val state = podcastPlayerDriver.state.value
    val snapshot = state.toPersistedSnapshot()
    if (snapshot != null) {
        podcastShellStateWiring.persistPodcastPlaybackSnapshot(snapshot)
    } else if (!state.hasActiveEpisode) {
        // Only a torn-down session clears the playlist. An active (e.g. paused before 10s)
        // episode stays resumable so the foreground poller can't observe a clear and stop it.
        podcastShellStateWiring.clearPodcastPlaybackSnapshot()
        workspaceRoot?.let { podcastPlaylistSync.clear(it) }
    }
}
