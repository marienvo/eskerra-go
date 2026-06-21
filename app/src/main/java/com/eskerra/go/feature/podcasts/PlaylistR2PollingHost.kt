package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2ConditionalResult
import com.eskerra.go.core.playlist.PlaylistEtagPoller
import com.eskerra.go.core.repository.PlaylistSyncRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lifecycle-gated foreground ETag-polling host, mirroring the polling layer in
 * `apps/mobile/src/features/podcasts/`. Activates the [PlaylistEtagPoller] when
 * **all three conditions** are met: app foreground, R2 configured, audio not playing.
 *
 * On `Updated` / `onRemotePlaylistCleared`:
 * - Bumps [playlistSyncGeneration] so observers can re-fetch.
 * - Invalidates the [PlaylistSyncRepository] coalesced read cache.
 *
 * Player integration: call [setPlaybackActive] from the audio player via [AppPodcastPlaylistEffects].
 */
class PlaylistR2PollingHost(
    private val syncRepository: PlaylistSyncRepository,
    private val workspaceRoot: File,
    private val isR2Configured: () -> Boolean,
    fetchConditional: suspend (etag: String?) -> R2ConditionalResult,
    scope: CoroutineScope,
    intervalMs: Long = POLL_INTERVAL_MS
) {
    private val _playlistSyncGeneration = MutableStateFlow(0)
    val playlistSyncGeneration: StateFlow<Int> = _playlistSyncGeneration.asStateFlow()

    private var appForeground = false
    private var playbackActive = false

    private val poller = PlaylistEtagPoller(
        initialIntervalMs = intervalMs,
        scope = scope,
        fetchConditional = fetchConditional,
        onDataChanged = { _: PlaylistEntry ->
            _playlistSyncGeneration.value++
            syncRepository.invalidateReadCache(workspaceRoot)
        },
        onRemotePlaylistCleared = {
            _playlistSyncGeneration.value++
            syncRepository.invalidateReadCache(workspaceRoot)
        }
    )

    /** Call from [ProcessLifecycleOwner] ON_START / ON_STOP. */
    fun setAppForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        updateActive()
    }

    /**
     * Call from the audio player when playback starts/stops.
     */
    fun setPlaybackActive(playing: Boolean) {
        if (playbackActive == playing) return
        playbackActive = playing
        updateActive()
    }

    fun dispose() = poller.dispose()

    fun getEtag(): String? = poller.getEtag()

    /** Re-evaluate activation when external inputs (for example R2 settings load) change. */
    fun refreshActiveState() {
        updateActive()
    }

    private fun updateActive() {
        poller.setActive(appForeground && isR2Configured() && !playbackActive)
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
