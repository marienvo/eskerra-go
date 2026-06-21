package com.eskerra.go.feature.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistReadOutcome
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.playlist.playbackSnapshot
import com.eskerra.go.core.playlist.reconcilePodcastPlaybackSources
import com.eskerra.go.core.playlist.shouldClearPlaylistForCatalog
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadPodcastCatalog: LoadPodcastCatalog,
    private val markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    private val podcastPlaylistSync: PodcastPlaylistSync,
    private val podcastPlayerDriver: PodcastPlayerDriver,
    private val syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
    private val loadPodcastArtwork: LoadPodcastArtwork,
    private val catalogSnapshotStore: PodcastCatalogSnapshotStore? = null,
    private val persistPodcastPlaybackSnapshot: PersistPodcastPlaybackSnapshot,
    private val clearPodcastPlaybackSnapshot: ClearPodcastPlaybackSnapshot,
    private val loadLocalSettings: LoadLocalSettings,
    private val onExitMiniPlayerArtworkMode: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastsUiState>(PodcastsUiState.Loading)
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    private val _refreshState = MutableStateFlow(PodcastRefreshState())
    val refreshState: StateFlow<PodcastRefreshState> = _refreshState.asStateFlow()

    private val _uiEvents = Channel<PodcastsUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    private val workspaceRoot: File? =
        WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()

    private var refreshJob: Job? = null
    private val autoMarkedEpisodeIds = mutableSetOf<String>()
    private val nearEndPlaylistClearedEpisodeIds = mutableSetOf<String>()

    private var lastCatalog: PodcastCatalog? = null
    private val playlistPersistence = PodcastPlaylistPersistence(
        scope = viewModelScope,
        workspaceRoot = workspaceRoot,
        podcastPlaylistSync = podcastPlaylistSync,
        persistPodcastPlaybackSnapshot = persistPodcastPlaybackSnapshot,
        clearPodcastPlaybackSnapshot = clearPodcastPlaybackSnapshot
    )
    private var playlistRestoredForVault = false
    private var userActionInFlight = false
    private var lastObservedPlaylistGeneration = 0

    private val selectionController = PodcastsSelectionController(
        scope = viewModelScope,
        readContent = { _uiState.value as? PodcastsUiState.Content },
        updateContent = { _uiState.value = it },
        onExitMiniPlayerArtworkMode = onExitMiniPlayerArtworkMode,
        markEpisodes = { episodes -> markPodcastEpisodesPlayed(config, filesDir, episodes) },
        onMarkedUpdated = {
            viewModelScope.launch { playlistPersistence.clearRemotePlaylist() }
            reloadCatalog()
        }
    )

    init {
        warmStartFromSnapshot()
        refresh()
        viewModelScope.launch {
            podcastPlayerDriver.state.collect { playerState ->
                updatePlayerState(playerState)
                handlePlaylistSideEffects(playerState)
                autoMarkNearEndOrEnded(playerState)
            }
        }
    }

    /**
     * Warm start: paint the persisted catalog snapshot before the full reload
     * finishes so Episodes shows cached sections instead of a spinner (spec §6.2).
     * Only applies while still [PodcastsUiState.Loading], so it never clobbers an
     * already-resolved fresh reload or error.
     */
    private fun warmStartFromSnapshot() {
        val store = catalogSnapshotStore ?: return
        viewModelScope.launch {
            val snapshot = store.read(config, filesDir) ?: return@launch
            if (_uiState.value !is PodcastsUiState.Loading) return@launch
            val playerState = podcastPlayerDriver.state.value
            if (snapshot.sections.isEmpty() && !playerState.hasActiveEpisode) return@launch
            lastCatalog = snapshot
            _uiState.value = PodcastsUiState.Content(
                sections = snapshot.sections,
                playerState = playerState
            )
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { reloadCatalog() }
    }

    /** Serialized RSS vault refresh (fetch + merge + commit), then catalog reload. */
    fun runVaultRefresh() {
        if (_refreshState.value.active) return
        _refreshState.value = PodcastRefreshState(active = true)
        viewModelScope.launch {
            try {
                val result = syncPodcastVaultRefresh(config, filesDir) { progress ->
                    _refreshState.value = _refreshState.value.copy(percent = progress.percent)
                }
                reloadCatalog()
                _refreshState.value = if (result.isSuccess) {
                    PodcastRefreshState()
                } else {
                    PodcastRefreshState(error = PodcastsActionError.RefreshFailed)
                }
            } catch (e: CancellationException) {
                _refreshState.value = PodcastRefreshState()
                throw e
            } catch (_: Exception) {
                _refreshState.value = PodcastRefreshState(error = PodcastsActionError.RefreshFailed)
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
                val previous = _uiState.value as? PodcastsUiState.Content
                _uiState.value = if (shouldShowEmpty) {
                    PodcastsUiState.Empty
                } else {
                    PodcastsUiState.Content(
                        sections = catalog.sections,
                        playerState = playerState,
                        selectedEpisodeIds = previous?.selectedEpisodeIds.orEmpty(),
                        markInFlight = false,
                        markError = null
                    )
                }
                catalogSnapshotStore?.save(config, filesDir, catalog)
                viewModelScope.launch {
                    loadPodcastArtwork.primeForCatalog(config, filesDir, catalog)
                }
            }
            .onFailure { error ->
                val catalogError = (error as? PodcastCatalogException)?.error
                    ?: PodcastCatalogError.LoadFailed(error.message)
                _uiState.value = PodcastsUiState.Error(catalogError)
            }
    }

    fun onPlaylistSyncGenerationChanged(generation: Int) {
        if (generation == lastObservedPlaylistGeneration) return
        lastObservedPlaylistGeneration = generation
        if (generation == 0) return
        viewModelScope.launch { syncPlaylistFromRemote() }
    }

    fun onEpisodeClick(episode: PodcastEpisode) {
        val current = _uiState.value as? PodcastsUiState.Content ?: return
        if (current.selectedEpisodeIds.isNotEmpty() || current.markInFlight) return
        val playback = podcastPlayerDriver.state.value
        if (playback.isActiveEpisode(episode) && playback.locksEpisodeSwitch) return
        if (playback.locksEpisodeSwitch && !playback.isActiveEpisode(episode)) {
            _uiEvents.trySend(PodcastsUiEvent.PauseToSwitchEpisode)
            return
        }
        launchUserAction {
            podcastPlayerDriver.play(episode, playlistPersistence.startPositionForEpisode(episode))
            queuePersist(podcastPlayerDriver.state.value)
        }
    }

    fun onEpisodeArtworkClick(episode: PodcastEpisode) {
        selectionController.onArtworkClick(episode)
    }

    fun clearSelection() {
        selectionController.clear()
    }

    fun markSelectedEpisodes() {
        selectionController.markSelected()
    }

    fun pausePlayback() = launchUserAction {
        podcastPlayerDriver.pause()
        queuePersist(podcastPlayerDriver.state.value)
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
        playlistPersistence.clearRemotePlaylist()
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

    fun seekTo(positionMs: Long) {
        podcastPlayerDriver.seekTo(positionMs)
        queuePersist(podcastPlayerDriver.state.value)
    }

    fun markEpisodesPlayed(episodes: List<PodcastEpisode>) {
        if (episodes.isEmpty()) return
        viewModelScope.launch {
            markPodcastEpisodesPlayed(config, filesDir, episodes)
                .onSuccess { result ->
                    if (result.updated) {
                        playlistPersistence.clearRemotePlaylist()
                        refresh()
                    }
                }
        }
    }

    private suspend fun restorePlaylistOnceIfNeeded(catalog: PodcastCatalog) {
        if (playlistRestoredForVault) return
        playlistRestoredForVault = true
        val root = workspaceRoot ?: return
        val entry =
            podcastPlaylistSync.read(root)?.also { playlistPersistence.knownPlaylistEntry = it }
                ?: return
        hydrateOrReset(catalog, entry)
    }

    private suspend fun syncPlaylistFromRemote() {
        val root = workspaceRoot ?: return
        if (hasActiveLocalSession()) return
        if (userActionInFlight) return
        val catalog = lastCatalog ?: return
        when (val outcome = podcastPlaylistSync.readOutcome(root)) {
            is PlaylistReadOutcome.Present -> {
                playlistPersistence.knownPlaylistEntry = outcome.entry
                hydrateOrReset(catalog, outcome.entry)
            }
            // R2 confirmed there is no playlist object. If this device still holds a resumable
            // session backed by its own local snapshot, the remote was lost (a write that never
            // landed before the app closed) rather than deliberately cleared elsewhere — keep the
            // session and re-publish it so the paused position survives on R2.
            PlaylistReadOutcome.Empty -> {
                val kept = playlistPersistence.republishResumableLocalSession(
                    state = podcastPlayerDriver.state.value,
                    snapshot = loadLocalSettings().playbackSnapshot(),
                    catalog = catalog
                )
                if (!kept) {
                    playlistPersistence.knownPlaylistEntry = null
                    resetPlaybackIfIdle()
                }
            }
            // Not configured or a transient failure: never destroy local state on an unknown remote.
            PlaylistReadOutcome.Unavailable -> Unit
        }
    }

    private suspend fun hydrateOrReset(catalog: PodcastCatalog, entry: PlaylistEntry) {
        val root = workspaceRoot ?: return
        val localSnapshot = loadLocalSettings().playbackSnapshot()
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = localSnapshot,
            remoteEntry = entry,
            nativeSession = podcastPlayerDriver.currentNativeSession()
        )
        if (hydration == null) {
            podcastPlaylistSync.clear(root)
            playlistPersistence.knownPlaylistEntry = null
            clearPodcastPlaybackSnapshot()
            resetPlaybackIfIdle()
            return
        }
        if (hasActiveLocalSession()) return
        val current = podcastPlayerDriver.state.value
        if (
            current.activeEpisode?.id == hydration.episode.id &&
            current.phase != PodcastPlaybackPhase.IDLE
        ) {
            return
        }
        podcastPlayerDriver.hydrate(
            episode = hydration.episode,
            positionMs = hydration.positionMs,
            durationMs = entry.durationMs ?: localSnapshot?.durationMs
        )
    }

    private suspend fun runPlaylistHousekeeping(catalog: PodcastCatalog) {
        val root = workspaceRoot ?: return
        val entry = playlistPersistence.knownPlaylistEntry ?: podcastPlaylistSync.read(root)?.also {
            playlistPersistence.knownPlaylistEntry = it
        }
        if (entry == null) return
        if (shouldClearPlaylistForCatalog(catalog, entry)) {
            podcastPlaylistSync.clear(root)
            playlistPersistence.knownPlaylistEntry = null
            if (!podcastPlayerDriver.state.value.isPlaying) {
                resetPlaybackIfIdle()
            }
        }
    }

    private fun validateActiveEpisodeStillInCatalog(catalog: PodcastCatalog) {
        val active = podcastPlayerDriver.state.value.activeEpisode ?: return
        if (catalog.allEpisodes.none { it.id == active.id }) {
            podcastPlayerDriver.stop()
            playlistPersistence.knownPlaylistEntry = null
        }
    }

    private fun handlePlaylistSideEffects(playerState: PodcastPlaybackState) {
        val episode = playerState.activeEpisode ?: return
        if (playerState.isNearEnd && nearEndPlaylistClearedEpisodeIds.add(episode.id)) {
            viewModelScope.launch { playlistPersistence.clearRemotePlaylist() }
        }
        if (playerState.isPlaying || playerState.isPaused) {
            playlistPersistence.queuePersist(playerState)
        }
    }

    private fun queuePersist(playerState: PodcastPlaybackState) {
        playlistPersistence.queuePersist(playerState)
    }

    /**
     * Tears down only a disposable resume hint ([PRIMED] or [STOPPED]). Live user sessions must
     * not be dropped by remote sync.
     */
    private fun resetPlaybackIfIdle() {
        val current = podcastPlayerDriver.state.value
        if (!current.hasActiveEpisode) return
        val disposableHint = current.phase == PodcastPlaybackPhase.PRIMED ||
            current.phase == PodcastPlaybackPhase.STOPPED
        if (disposableHint) {
            podcastPlayerDriver.stop()
        }
    }

    /** Remote playlist sync defers to a live user-facing session (not a [PRIMED] hint). */
    private fun hasActiveLocalSession(): Boolean {
        val current = podcastPlayerDriver.state.value
        if (!current.hasActiveEpisode) return false
        return when (current.phase) {
            PodcastPlaybackPhase.PLAYING,
            PodcastPlaybackPhase.NEAR_END_PLAYING,
            PodcastPlaybackPhase.PAUSED,
            PodcastPlaybackPhase.NEAR_END_PAUSED,
            PodcastPlaybackPhase.LOADING -> true

            else -> false
        }
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
                        playlistPersistence.clearRemotePlaylist()
                        refresh()
                    }
                    if (playerState.phase == PodcastPlaybackPhase.ENDED) {
                        podcastPlayerDriver.stop()
                    }
                }
        }
    }

    companion object {
        const val MIN_PROGRESS_MS = 10_000L

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadPodcastCatalog: LoadPodcastCatalog,
            markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
            podcastPlaylistSync: PodcastPlaylistSync,
            podcastPlayerDriver: PodcastPlayerDriver,
            syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
            loadPodcastArtwork: LoadPodcastArtwork,
            catalogSnapshotStore: PodcastCatalogSnapshotStore? = null,
            persistPodcastPlaybackSnapshot: PersistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot: ClearPodcastPlaybackSnapshot,
            loadLocalSettings: LoadLocalSettings,
            onExitMiniPlayerArtworkMode: () -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PodcastsViewModel(
                config,
                filesDir,
                loadPodcastCatalog,
                markPodcastEpisodesPlayed,
                podcastPlaylistSync,
                podcastPlayerDriver,
                syncPodcastVaultRefresh,
                loadPodcastArtwork,
                catalogSnapshotStore,
                persistPodcastPlaybackSnapshot,
                clearPodcastPlaybackSnapshot,
                loadLocalSettings,
                onExitMiniPlayerArtworkMode
            ) as T
        }
    }
}
