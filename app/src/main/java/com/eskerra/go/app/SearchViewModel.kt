package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.feature.search.SearchUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val searchVault: SearchVault,
    private val maintainVaultSearchIndex: MaintainVaultSearchIndex
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var activeSearchId: Long = 0L
    private var activeVaultInstanceId: String = ""
    private var lastResults: List<com.eskerra.go.core.search.VaultSearchNoteResult> = emptyList()

    init {
        warmIndex()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        scheduleSearch()
    }

    fun warmIndex() {
        viewModelScope.launch {
            _uiState.value = if (_query.value.isBlank()) {
                SearchUiState.Idle
            } else {
                SearchUiState.Opening(_query.value, lastResults)
            }
            maintainVaultSearchIndex(config, filesDir)
            scheduleSearch(immediate = _query.value.isBlank())
        }
    }

    fun reconcileIndex() {
        viewModelScope.launch {
            maintainVaultSearchIndex(config, filesDir)
        }
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val trimmed = _query.value.trim()
            if (trimmed.isEmpty()) {
                _uiState.value = SearchUiState.Idle
                lastResults = emptyList()
                return@launch
            }
            if (!immediate) {
                delay(DEBOUNCE_MS)
            }
            val searchId = ++activeSearchId
            _uiState.value = SearchUiState.Searching(trimmed, lastResults)
            delay(HOLD_PREVIOUS_MS)
            val outcome = searchVault(config, filesDir, trimmed, searchId).getOrElse {
                _uiState.value = SearchUiState.Error("Search is unavailable.")
                return@launch
            }
            if (searchId != activeSearchId) return@launch
            if (outcome.vaultInstanceId.isNotEmpty()) {
                activeVaultInstanceId = outcome.vaultInstanceId
            }
            if (outcome.searchId != searchId) return@launch
            if (activeVaultInstanceId.isNotEmpty() &&
                outcome.vaultInstanceId != activeVaultInstanceId
            ) {
                return@launch
            }
            if (outcome.notes.isEmpty()) {
                _uiState.value = SearchUiState.NoMatches(
                    query = trimmed,
                    indexReady = outcome.status.indexReady,
                    bodiesIndexReady = outcome.status.bodiesIndexReady
                )
                lastResults = emptyList()
            } else {
                lastResults = outcome.notes
                _uiState.value = SearchUiState.Results(
                    query = trimmed,
                    notes = outcome.notes,
                    indexReady = outcome.status.indexReady,
                    bodiesIndexReady = outcome.status.bodiesIndexReady,
                    resultCountLabel = "${outcome.notes.size} notes found"
                )
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 260L
        private const val HOLD_PREVIOUS_MS = 100L

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            searchVault: SearchVault,
            maintainVaultSearchIndex: MaintainVaultSearchIndex
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchViewModel(config, filesDir, searchVault, maintainVaultSearchIndex) as T
        }
    }
}
