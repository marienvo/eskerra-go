package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.feature.podcasts.PlaylistR2PollingHost

@Composable
internal fun AppPodcastPlaylistEffects(
    pollingHost: PlaylistR2PollingHost?,
    podcastPlayerDriver: PodcastPlayerDriver
) {
    val playerState by podcastPlayerDriver.state.collectAsState()

    DisposableEffect(pollingHost) {
        val host = pollingHost ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> host.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> host.setAppForeground(false)
                else -> Unit
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
            host.dispose()
        }
    }

    LaunchedEffect(playerState.isPlaying, pollingHost) {
        pollingHost?.setPlaybackActive(playerState.isPlaying)
    }

    LaunchedEffect(pollingHost) {
        pollingHost?.setAppForeground(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }
}
