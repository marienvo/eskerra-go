package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackSnapshot
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
    var knownPlaylistEntry: PlaylistEntry? = null

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
                is PlaylistWriteResult.Saved -> result.entry
                is PlaylistWriteResult.Superseded -> result.entry
                PlaylistWriteResult.Skipped -> knownPlaylistEntry
            }
        }
    }

    /**
     * Re-publishes the active session to R2 when the remote is empty but a matching local snapshot
     * proves this device owns a still-resumable session — the remote was lost (a write that never
     * landed before the app closed) rather than deliberately cleared elsewhere. Returns true when
     * the session was kept (and a re-publish attempted), false when there is nothing to preserve.
     */
    suspend fun republishResumableLocalSession(
        state: PodcastPlaybackState,
        snapshot: PodcastPlaybackSnapshot?,
        catalog: PodcastCatalog
    ): Boolean {
        val root = workspaceRoot ?: return false
        snapshot ?: return false
        val active = state.activeEpisode ?: return false
        if (active.id != snapshot.episodeId) return false
        if (
            state.phase == PodcastPlaybackPhase.IDLE ||
            state.phase == PodcastPlaybackPhase.STOPPED
        ) {
            return false
        }
        val episode = catalog.allEpisodes.firstOrNull { it.id == active.id } ?: return false
        if (episode.isListened) return false
        val result = podcastPlaylistSync.persist(
            workspaceRoot = root,
            episode = episode,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            knownEntry = null
        )
        knownPlaylistEntry = when (result) {
            is PlaylistWriteResult.Saved -> result.entry
            is PlaylistWriteResult.Superseded -> result.entry
            PlaylistWriteResult.Skipped -> knownPlaylistEntry
        }
        return true
    }

    fun startPositionForEpisode(episode: PodcastEpisode): Long {
        val entry = knownPlaylistEntry ?: return 0L
        return if (entry.episodeId == episode.id) entry.positionMs.coerceAtLeast(0L) else 0L
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
