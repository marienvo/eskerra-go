package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadInboxSummaries: LoadInboxSummariesCached
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private val _showRefreshIndicator = MutableStateFlow(false)
    val showRefreshIndicator: StateFlow<Boolean> = _showRefreshIndicator.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val cached = loadInboxSummaries.readCached(config, filesDir)
            if (cached != null) {
                _uiState.value = cached.toInboxUiState(isRefreshing = true)
            }
            refresh(showFullScreenLoading = cached == null)
        }
    }

    fun refresh() {
        refresh(showFullScreenLoading = _uiState.value !is InboxUiState.Content)
    }

    private fun refresh(showFullScreenLoading: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            var indicatorJob: Job? = null
            if (showFullScreenLoading) {
                _uiState.value = InboxUiState.Loading
                _showRefreshIndicator.value = false
            } else {
                markRefreshing()
                _showRefreshIndicator.value = false
                indicatorJob = launch {
                    delay(REFRESH_INDICATOR_DELAY_MS)
                    val content = _uiState.value as? InboxUiState.Content
                    if (content?.isRefreshing == true) {
                        _showRefreshIndicator.value = true
                    }
                }
            }

            loadInboxSummaries(config, filesDir).fold(
                onSuccess = { notes ->
                    _showRefreshIndicator.value = false
                    _uiState.value = notes.toInboxUiState(isRefreshing = false)
                },
                onFailure = { error ->
                    _showRefreshIndicator.value = false
                    if (_uiState.value is InboxUiState.Content) {
                        markRefreshing(false)
                    } else {
                        _uiState.value = InboxUiState.Error(mapScanFailure(error))
                    }
                }
            )
            indicatorJob?.cancel()
        }
    }

    private fun markRefreshing(isRefreshing: Boolean = true) {
        when (val state = _uiState.value) {
            is InboxUiState.Content -> _uiState.value = state.copy(isRefreshing = isRefreshing)
            InboxUiState.Empty -> if (isRefreshing) {
                _uiState.value = InboxUiState.Content(emptyList(), isRefreshing = true)
            }
            else -> Unit
        }
    }

    private fun List<com.eskerra.go.core.model.NoteSummary>.toInboxUiState(
        isRefreshing: Boolean
    ): InboxUiState = when {
        isEmpty() && !isRefreshing -> InboxUiState.Empty
        isEmpty() -> InboxUiState.Content(emptyList(), isRefreshing = true)
        else -> InboxUiState.Content(this, isRefreshing = isRefreshing)
    }

    private fun mapScanFailure(error: Throwable): String {
        val indexError = (error as? NoteIndexException)?.error
        return when (indexError) {
            is NoteIndexError.InvalidWorkspacePath -> WORKSPACE_UNAVAILABLE_MESSAGE
            is NoteIndexError.WorkspaceMissing -> WORKSPACE_MISSING_MESSAGE
            is NoteIndexError.ScanFailed, null -> SCAN_ERROR_MESSAGE
        }
    }

    companion object {
        internal const val REFRESH_INDICATOR_DELAY_MS = 300L
        const val SCAN_ERROR_MESSAGE = "Could not scan workspace notes."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadInboxSummaries: LoadInboxSummariesCached
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InboxViewModel(config, filesDir, loadInboxSummaries) as T
        }
    }
}
