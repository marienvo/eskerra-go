package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eskerra.go.core.repository.PlaylistSyncRepository
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.data.r2.PlaylistR2ConditionalFetch
import com.eskerra.go.feature.podcasts.PlaylistR2PollingHost
import java.io.File

@Composable
internal fun rememberPlaylistR2PollingHost(
    workspaceRoot: File?,
    loadVaultSettings: LoadVaultSettings,
    playlistSyncRepository: PlaylistSyncRepository,
    playlistR2ConditionalFetch: PlaylistR2ConditionalFetch,
    podcastPlayerDriver: PodcastPlayerDriver
): PlaylistR2PollingHost? {
    val scope = rememberCoroutineScope()
    var r2Configured by remember(workspaceRoot) { mutableStateOf(false) }
    LaunchedEffect(workspaceRoot) {
        r2Configured = workspaceRoot?.let { root ->
            loadVaultSettings(root).getOrNull()
                ?.let(R2Settings::isVaultR2PlaylistConfigured)
        } == true
    }
    val pollingHost = remember(
        workspaceRoot,
        scope,
        playlistSyncRepository,
        playlistR2ConditionalFetch
    ) {
        workspaceRoot?.let { root ->
            PlaylistR2PollingHost(
                syncRepository = playlistSyncRepository,
                workspaceRoot = root,
                isR2Configured = { r2Configured },
                fetchConditional = { etag -> playlistR2ConditionalFetch.fetch(root, etag) },
                scope = scope
            )
        }
    }
    LaunchedEffect(r2Configured, pollingHost) {
        pollingHost?.refreshActiveState()
    }
    AppPodcastPlaylistEffects(
        pollingHost = pollingHost,
        podcastPlayerDriver = podcastPlayerDriver
    )
    return pollingHost
}
