package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.playlist.toPersistedSnapshot
import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PodcastPlaylistPersistence(
    private val scope: CoroutineScope,
    private val workspaceRoot: File?,
    private val podcastPlaylistSync: PodcastPlaylistSync,
    private val persistPodcastPlaybackSnapshot: PersistPodcastPlaybackSnapshot,
    private val clearPodcastPlaybackSnapshot: ClearPodcastPlaybackSnapshot
) {
    private var persistJob: Job? = null
    var knownPlaylistEntry: com.eskerra.go.core.model.PlaylistEntry? = null

    fun queuePersist(playerState: PodcastPlaybackState) {
        val episode = playerState.activeEpisode ?: return
        val root = workspaceRoot ?: return
        if (
            playerState.phase == PodcastPlaybackPhase.LOADING &&
            playerState.positionMs < PodcastsViewModel.MIN_PROGRESS_MS
        ) {
            return
        }
        playerState.toPersistedSnapshot()?.let { snapshot ->
            scope.launch { persistPodcastPlaybackSnapshot(snapshot) }
        }
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            if (!podcastPlaylistSync.isR2Configured(root)) return@launch
            val result = podcastPlaylistSync.persist(
                workspaceRoot = root,
                episode = episode,
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                knownEntry = knownPlaylistEntry
            )
            knownPlaylistEntry = when (result) {
                is com.eskerra.go.core.model.PlaylistWriteResult.Saved -> result.entry
                is com.eskerra.go.core.model.PlaylistWriteResult.Superseded -> result.entry
                com.eskerra.go.core.model.PlaylistWriteResult.Skipped -> knownPlaylistEntry
            }
        }
    }

    suspend fun clearRemotePlaylist() {
        val root = workspaceRoot ?: return
        podcastPlaylistSync.clear(root)
        knownPlaylistEntry = null
        clearPodcastPlaybackSnapshot()
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 500L
    }
}
