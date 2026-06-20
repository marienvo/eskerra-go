package com.eskerra.go.feature.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadPodcastCatalog: LoadPodcastCatalog,
    private val markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    private val podcastPlayerDriver: PodcastPlayerDriver
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastsUiState>(PodcastsUiState.Loading)
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private val autoMarkedEpisodeIds = mutableSetOf<String>()

    init {
        refresh()
        viewModelScope.launch {
            podcastPlayerDriver.state.collect { playerState ->
                updatePlayerState(playerState)
                autoMarkNearEndOrEnded(playerState)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (_uiState.value !is PodcastsUiState.Content) {
                _uiState.value = PodcastsUiState.Loading
            }
            loadPodcastCatalog(config, filesDir)
                .onSuccess { catalog ->
                    val playerState = podcastPlayerDriver.state.value
                    val shouldShowEmpty =
                        catalog.sections.isEmpty() &&
                            !playerState.hasActiveEpisode
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
    }

    fun onEpisodeClick(episode: PodcastEpisode) {
        podcastPlayerDriver.play(episode)
    }

    fun pausePlayback() {
        podcastPlayerDriver.pause()
    }

    fun resumePlayback() {
        podcastPlayerDriver.resume()
    }

    fun stopPlayback() {
        podcastPlayerDriver.stop()
    }

    fun seekBy(deltaMs: Long) {
        podcastPlayerDriver.seekBy(deltaMs)
    }

    /**
     * Marks one or more episodes as played: flips the markdown checkbox, commits the
     * change to git on its own channel, and reloads the catalog so the played
     * episodes disappear from the list.
     */
    fun markEpisodesPlayed(episodes: List<PodcastEpisode>) {
        if (episodes.isEmpty()) return
        viewModelScope.launch {
            markPodcastEpisodesPlayed(config, filesDir, episodes)
                .onSuccess { result -> if (result.updated) refresh() }
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
                    if (result.updated) refresh()
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
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."
        const val LOAD_ERROR_MESSAGE = "Could not load podcast episodes."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadPodcastCatalog: LoadPodcastCatalog,
            markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
            podcastPlayerDriver: PodcastPlayerDriver
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PodcastsViewModel(
                config,
                filesDir,
                loadPodcastCatalog,
                markPodcastEpisodesPlayed,
                podcastPlayerDriver
            ) as T
        }
    }
}
