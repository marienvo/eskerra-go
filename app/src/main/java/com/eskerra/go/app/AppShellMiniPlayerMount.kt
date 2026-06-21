package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import java.io.File

internal data class AppShellMiniPlayerMount(
    val visible: Boolean,
    val content: (@Composable () -> Unit)?
)

@Composable
internal fun rememberAppShellMiniPlayerMount(
    currentConfig: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    podcastPlayerDriver: PodcastPlayerDriver,
    bridge: PodcastShellBridge
): AppShellMiniPlayerMount {
    val playerState by podcastPlayerDriver.state.collectAsState()
    val visible = playerState.hasActiveEpisode
    val content: (@Composable () -> Unit)? = if (visible) {
        {
            AppPodcastMiniPlayerHost(
                config = currentConfig,
                filesDir = filesDir,
                loadPodcastArtwork = loadPodcastArtwork,
                markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
                podcastPlayerDriver = podcastPlayerDriver,
                bridge = bridge
            )
        }
    } else {
        null
    }
    return AppShellMiniPlayerMount(visible = visible, content = content)
}
