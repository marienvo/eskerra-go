package com.eskerra.go.feature.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.playlist.resolvePodcastPlaylistHydration
import com.eskerra.go.core.playlist.shouldClearPlaylistForCatalog
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadPodcastCatalog: LoadPodcastCatalog,
    private val markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    private val podcastPlaylistSync: PodcastPlaylistSync,
    private val podcastPlayerDriver: PodcastPlayerDriver,
    private val syncPodcastVaultRefresh: SyncPodcastVaultRefresh
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastsUiState>(PodcastsUiState.Loading)
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    private val _refreshState = MutableStateFlow(PodcastRefreshState())
    val refreshState: StateFlow<PodcastRefreshState> = _refreshState.asStateFlow()

    private val workspaceRoot: File? =
        WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()

    private var refreshJob: Job? = null
    private var persistJob: Job? = null
    private val autoMarkedEpisodeIds = mutableSetOf<String>()
    private val nearEndPlaylistClearedEpisodeIds = mutableSetOf<String>()

    private var lastCatalog: PodcastCatalog? = null
    private var knownPlaylistEntry: PlaylistEntry? = null
    private var playlistRestoredForVault = false
    private var userActionInFlight = false
    private var lastObservedPlaylistGeneration = 0

    init {
        refresh()
        viewModelScope.launch {
            podcastPlayerDriver.state.collect { playerState ->
                updatePlayerState(playerState)
                handlePlaylistSideEffects(playerState)
                autoMarkNearEndOrEnded(playerState)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { reloadCatalog() }
    }

    /** Serialized RSS vault refresh (fetch + merge + commit), then catalog reload. */
    fun runVaultRefresh() {
        if (_refreshState.value.active) return
        viewModelScope.launch {
            _refreshState.value = PodcastRefreshState(active = true)
            val result = syncPodcastVaultRefresh(config, filesDir) { progress ->
                _refreshState.value = _refreshState.value.copy(percent = progress.percent)
            }
            reloadCatalog()
            _refreshState.value = if (result.isSuccess) {
                PodcastRefreshState()
            } else {
                PodcastRefreshState(error = REFRESH_ERROR_MESSAGE)
            }
        }
    }

    private suspend fun reloadCatalog() {
        if (_uiState.value !is PodcastsUiState.Content) {
            _uiState.value = PodcastsUiState.Loading
        }
        loadPodcastCatalog(config, filesDir)
            .onSuccess { catalog ->
                lastCatalog = catalog
                runPlaylistHousekeeping(catalog)
                validateActiveEpisodeStillInCatalog(catalog)
                restorePlaylistOnceIfNeeded(catalog)
                val playerState = podcastPlayerDriver.state.value
                val shouldShowEmpty = catalog.sections.isEmpty() && !playerState.hasActiveEpisode
                _uiState.value = if (shouldShowEmpty) {
                    PodcastsUiState.Empty
                } else {
                    PodcastsUiState.Content(
                        sections = catalog.sections,
                        playerState = playerState
                    )
                }
            }
            .onFailure { error ->
                _uiState.value = PodcastsUiState.Error(mapFailure(error))
            }
    }

    fun onPlaylistSyncGenerationChanged(generation: Int) {
        if (generation == lastObservedPlaylistGeneration) return
        lastObservedPlaylistGeneration = generation
        if (generation == 0) return
        viewModelScope.launch { syncPlaylistFromRemote() }
    }

    fun onEpisodeClick(episode: PodcastEpisode) {
        val current = podcastPlayerDriver.state.value
        if (current.isPlaying && current.isActiveEpisode(episode)) return
        launchUserAction {
            podcastPlayerDriver.play(episode, startPositionForEpisode(episode))
            queuePersist(podcastPlayerDriver.state.value)
        }
    }

    fun pausePlayback() = launchUserAction {
        podcastPlayerDriver.pause()
        val state = podcastPlayerDriver.state.value
        if (state.positionMs < MIN_PROGRESS_MS) clearRemotePlaylist() else queuePersist(state)
    }

    fun resumePlayback() {
        val state = podcastPlayerDriver.state.value
        val episode = state.activeEpisode ?: return
        if (state.isPlaying) return
        launchUserAction {
            when (state.phase) {
                PodcastPlaybackPhase.PRIMED,
                PodcastPlaybackPhase.PAUSED,
                PodcastPlaybackPhase.NEAR_END_PAUSED,
                PodcastPlaybackPhase.STOPPED ->
                    podcastPlayerDriver.play(episode, state.positionMs)

                else -> podcastPlayerDriver.resume()
            }
            queuePersist(podcastPlayerDriver.state.value)
        }
    }

    fun stopPlayback() = launchUserAction {
        podcastPlayerDriver.stop()
        clearRemotePlaylist()
    }

    private fun launchUserAction(block: suspend () -> Unit) {
        userActionInFlight = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                userActionInFlight = false
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        podcastPlayerDriver.seekBy(deltaMs)
        queuePersist(podcastPlayerDriver.state.value)
    }

    fun markEpisodesPlayed(episodes: List<PodcastEpisode>) {
        if (episodes.isEmpty()) return
        viewModelScope.launch {
            markPodcastEpisodesPlayed(config, filesDir, episodes)
                .onSuccess { result ->
                    if (result.updated) {
                        clearRemotePlaylist()
                        refresh()
                    }
                }
        }
    }

    private suspend fun restorePlaylistOnceIfNeeded(catalog: PodcastCatalog) {
        if (playlistRestoredForVault) return
        playlistRestoredForVault = true
        val root = workspaceRoot ?: return
        val entry = podcastPlaylistSync.read(root)?.also { knownPlaylistEntry = it } ?: return
        hydrateOrReset(catalog, entry)
    }

    private suspend fun syncPlaylistFromRemote() {
        val root = workspaceRoot ?: return
        if (podcastPlayerDriver.state.value.isPlaying) return
        if (userActionInFlight) return
        val catalog = lastCatalog ?: return
        val entry = podcastPlaylistSync.read(root)
        knownPlaylistEntry = entry
        if (entry == null) {
            resetPlaybackIfIdle()
            return
        }
        hydrateOrReset(catalog, entry)
    }

    private suspend fun hydrateOrReset(catalog: PodcastCatalog, entry: PlaylistEntry) {
        val root = workspaceRoot ?: return
        val hydration = resolvePodcastPlaylistHydration(catalog, entry)
        if (hydration == null) {
            podcastPlaylistSync.clear(root)
            knownPlaylistEntry = null
            resetPlaybackIfIdle()
            return
        }
        val current = podcastPlayerDriver.state.value
        if (current.isPlaying) return
        if (
            current.activeEpisode?.id == hydration.episode.id &&
            current.phase != PodcastPlaybackPhase.IDLE
        ) {
            return
        }
        podcastPlayerDriver.hydrate(
            episode = hydration.episode,
            positionMs = hydration.positionMs,
            durationMs = entry.durationMs
        )
    }

    private suspend fun runPlaylistHousekeeping(catalog: PodcastCatalog) {
        val root = workspaceRoot ?: return
        val entry = knownPlaylistEntry ?: podcastPlaylistSync.read(root)?.also {
            knownPlaylistEntry = it
        }
        if (entry == null) return
        if (shouldClearPlaylistForCatalog(catalog, entry)) {
            podcastPlaylistSync.clear(root)
            knownPlaylistEntry = null
            if (!podcastPlayerDriver.state.value.isPlaying) {
                resetPlaybackIfIdle()
            }
        }
    }

    private fun validateActiveEpisodeStillInCatalog(catalog: PodcastCatalog) {
        val active = podcastPlayerDriver.state.value.activeEpisode ?: return
        if (catalog.allEpisodes.none { it.id == active.id }) {
            podcastPlayerDriver.stop()
            knownPlaylistEntry = null
        }
    }

    private fun handlePlaylistSideEffects(playerState: PodcastPlaybackState) {
        val episode = playerState.activeEpisode ?: return
        if (playerState.isNearEnd && nearEndPlaylistClearedEpisodeIds.add(episode.id)) {
            viewModelScope.launch { clearRemotePlaylist() }
        }
        if (playerState.isPlaying || playerState.isPaused) {
            queuePersist(playerState)
        }
    }

    private fun queuePersist(playerState: PodcastPlaybackState) {
        val episode = playerState.activeEpisode ?: return
        val root = workspaceRoot ?: return
        if (
            playerState.phase == PodcastPlaybackPhase.LOADING &&
            playerState.positionMs < MIN_PROGRESS_MS
        ) {
            return
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
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

    private suspend fun clearRemotePlaylist() {
        val root = workspaceRoot ?: return
        podcastPlaylistSync.clear(root)
        knownPlaylistEntry = null
    }

    private fun resetPlaybackIfIdle() {
        val current = podcastPlayerDriver.state.value
        if (!current.isPlaying && current.hasActiveEpisode) {
            podcastPlayerDriver.stop()
        }
    }

    private fun startPositionForEpisode(episode: PodcastEpisode): Long {
        val entry = knownPlaylistEntry ?: return 0L
        return if (entry.episodeId == episode.id) entry.positionMs.coerceAtLeast(0L) else 0L
    }

    private fun updatePlayerState(playerState: PodcastPlaybackState) {
        val current = _uiState.value
        if (current is PodcastsUiState.Content) {
            _uiState.value = current.copy(playerState = playerState)
        }
    }

    private fun autoMarkNearEndOrEnded(playerState: PodcastPlaybackState) {
        val episode = playerState.activeEpisode ?: return
        val shouldMark = playerState.phase == PodcastPlaybackPhase.NEAR_END_PLAYING ||
            playerState.phase == PodcastPlaybackPhase.NEAR_END_PAUSED ||
            playerState.phase == PodcastPlaybackPhase.ENDED
        if (!shouldMark || !autoMarkedEpisodeIds.add(episode.id)) return

        viewModelScope.launch {
            markPodcastEpisodesPlayed(config, filesDir, listOf(episode))
                .onSuccess { result ->
                    if (result.updated) {
                        clearRemotePlaylist()
                        refresh()
                    }
                    if (playerState.phase == PodcastPlaybackPhase.ENDED) {
                        podcastPlayerDriver.stop()
                    }
                }
        }
    }

    private fun mapFailure(error: Throwable): String {
        val catalogError = (error as? PodcastCatalogException)?.error
        return when (catalogError) {
            PodcastCatalogError.InvalidWorkspacePath -> WORKSPACE_UNAVAILABLE_MESSAGE
            PodcastCatalogError.WorkspaceMissing -> WORKSPACE_MISSING_MESSAGE
            is PodcastCatalogError.LoadFailed -> LOAD_ERROR_MESSAGE
            null -> LOAD_ERROR_MESSAGE
        }
    }

    companion object {
        const val MIN_PROGRESS_MS = 10_000L
        private const val PERSIST_DEBOUNCE_MS = 500L

        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."
        const val LOAD_ERROR_MESSAGE = "Could not load podcast episodes."
        const val REFRESH_ERROR_MESSAGE = "Could not refresh podcast episodes."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadPodcastCatalog: LoadPodcastCatalog,
            markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
            podcastPlaylistSync: PodcastPlaylistSync,
            podcastPlayerDriver: PodcastPlayerDriver,
            syncPodcastVaultRefresh: SyncPodcastVaultRefresh
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PodcastsViewModel(
                config,
                filesDir,
                loadPodcastCatalog,
                markPodcastEpisodesPlayed,
                podcastPlaylistSync,
                podcastPlayerDriver,
                syncPodcastVaultRefresh
            ) as T
        }
    }
}
