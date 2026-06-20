package com.eskerra.go.feature.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadPodcastCatalog: LoadPodcastCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastsUiState>(PodcastsUiState.Loading)
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (_uiState.value !is PodcastsUiState.Content) {
                _uiState.value = PodcastsUiState.Loading
            }
            loadPodcastCatalog(config, filesDir)
                .onSuccess { catalog ->
                    _uiState.value = if (catalog.sections.isEmpty()) {
                        PodcastsUiState.Empty
                    } else {
                        PodcastsUiState.Content(catalog.sections)
                    }
                }
                .onFailure { error ->
                    _uiState.value = PodcastsUiState.Error(mapFailure(error))
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onEpisodeClick(episode: PodcastEpisode) {
        // Playback wiring lands in phase 4.
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
            loadPodcastCatalog: LoadPodcastCatalog
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PodcastsViewModel(
                config,
                filesDir,
                loadPodcastCatalog
            ) as T
        }
    }
}
