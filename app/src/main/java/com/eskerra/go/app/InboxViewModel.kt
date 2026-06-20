package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.DeleteInboxNoteError
import com.eskerra.go.core.model.DeleteInboxNoteException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadInboxSummaries: LoadInboxSummariesCached,
    private val deleteInboxNotes: DeleteInboxNotes,
    private val onInboxMutated: (List<String>) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private val _selectedNoteIds = MutableStateFlow<Set<NoteId>>(emptySet())
    val selectedNoteIds: StateFlow<Set<NoteId>> = _selectedNoteIds.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private var refreshJob: Job? = null
    private var deleteJob: Job? = null

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

    fun toggleSelection(noteId: NoteId) {
        if (_isDeleting.value) return
        _deleteError.value = null
        _selectedNoteIds.value = _selectedNoteIds.value.toMutableSet().apply {
            if (contains(noteId)) remove(noteId) else add(noteId)
        }
    }

    fun clearSelection() {
        _deleteError.value = null
        _selectedNoteIds.value = emptySet()
    }

    fun deleteSelected() {
        if (_isDeleting.value) return
        val selected = _selectedNoteIds.value
        if (selected.isEmpty()) return

        val availableNotes = (_uiState.value as? InboxUiState.Content)?.notes.orEmpty()
        val resolvedIds = selected.filter { noteId ->
            availableNotes.any { it.id == noteId }
        }
        if (resolvedIds.isEmpty()) return

        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            _deleteError.value = null
            _isDeleting.value = true
            deleteInboxNotes(config, filesDir, resolvedIds, availableNotes).fold(
                onSuccess = {
                    _selectedNoteIds.value = emptySet()
                    _isDeleting.value = false
                    onInboxMutated(resolvedIds.map { it.value })
                    refresh(showFullScreenLoading = false)
                },
                onFailure = { error ->
                    _isDeleting.value = false
                    _deleteError.value = mapDeleteFailure(error)
                }
            )
        }
    }

    private fun refresh(showFullScreenLoading: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (showFullScreenLoading && _uiState.value !is InboxUiState.Content) {
                _uiState.value = InboxUiState.Loading
            } else if (showFullScreenLoading) {
                markRefreshing()
            } else {
                markRefreshing()
            }

            loadInboxSummaries(config, filesDir).fold(
                onSuccess = { notes ->
                    val currentNotes = (_uiState.value as? InboxUiState.Content)?.notes
                    if (notes == currentNotes) {
                        markRefreshing(isRefreshing = false)
                    } else {
                        _uiState.value = notes.toInboxUiState(isRefreshing = false)
                    }
                },
                onFailure = { error ->
                    if (_uiState.value is InboxUiState.Content) {
                        markRefreshing(false)
                    } else {
                        _uiState.value = InboxUiState.Error(mapScanFailure(error))
                    }
                }
            )
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

    private fun mapDeleteFailure(error: Throwable): String {
        val deleteError = (error as? DeleteInboxNoteException)?.error
        return when (deleteError) {
            DeleteInboxNoteError.NotInInbox -> DeleteInboxNotes.NOT_IN_INBOX_MESSAGE
            DeleteInboxNoteError.StaleEntry -> DeleteInboxNotes.STALE_ENTRY_MESSAGE
            DeleteInboxNoteError.InvalidWorkspacePath -> WORKSPACE_UNAVAILABLE_MESSAGE
            DeleteInboxNoteError.WorkspaceMissing -> WORKSPACE_MISSING_MESSAGE
            is DeleteInboxNoteError.DeleteFailed,
            is DeleteInboxNoteError.RegistryRefreshFailed,
            null -> DELETE_ERROR_MESSAGE
        }
    }

    companion object {
        const val SCAN_ERROR_MESSAGE = "Could not scan workspace notes."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."
        const val DELETE_ERROR_MESSAGE = "Could not delete selected entries."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadInboxSummaries: LoadInboxSummariesCached,
            deleteInboxNotes: DeleteInboxNotes,
            onInboxMutated: (List<String>) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = InboxViewModel(
                config,
                filesDir,
                loadInboxSummaries,
                deleteInboxNotes,
                onInboxMutated
            ) as T
        }
    }
}
