package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.CreateNoteError
import com.eskerra.go.core.model.CreateNoteException
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.SaveNoteError
import com.eskerra.go.core.model.SaveNoteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.feature.editor.CreateInboxUiState
import com.eskerra.go.feature.editor.NoteEditorUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val noteId: NoteId,
    private val loadEditableNote: LoadEditableNote,
    private val saveNote: SaveNote,
    private val loadGitStatusSummary: LoadGitStatusSummary
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteEditorUiState>(NoteEditorUiState.Loading)
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun updateDraft(markdown: String) {
        val current = _uiState.value
        if (current !is NoteEditorUiState.Content || !current.note.canEdit || current.isSaving) {
            return
        }
        _uiState.value = current.copy(
            draftMarkdown = markdown,
            isDirty = markdown != current.note.markdown,
            saveMessage = null,
            errorMessage = null
        )
    }

    fun save() {
        val current = _uiState.value
        if (current !is NoteEditorUiState.Content || !current.note.canEdit || current.isSaving) {
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, errorMessage = null, saveMessage = null)
            saveNote(config, filesDir, noteId, current.draftMarkdown).fold(
                onSuccess = { result ->
                    _uiState.value = NoteEditorUiState.Content(
                        note = result.note,
                        draftMarkdown = result.note.markdown,
                        isDirty = false,
                        isSaving = false,
                        saveMessage = SAVED_MESSAGE,
                        errorMessage = null,
                        gitStatus = result.gitStatus
                    )
                },
                onFailure = { error ->
                    val failed = _uiState.value as? NoteEditorUiState.Content ?: current
                    _uiState.value = failed.copy(
                        isSaving = false,
                        errorMessage = mapSaveFailure(error)
                    )
                }
            )
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = NoteEditorUiState.Loading
            loadEditableNote(config, filesDir, noteId).fold(
                onSuccess = { note ->
                    val gitStatus = loadGitStatusSummary(config, filesDir)
                    _uiState.value = NoteEditorUiState.Content(
                        note = note,
                        draftMarkdown = note.markdown,
                        isDirty = false,
                        isSaving = false,
                        saveMessage = null,
                        errorMessage = null,
                        gitStatus = gitStatus
                    )
                },
                onFailure = { error ->
                    _uiState.value = mapLoadFailure(error)
                }
            )
        }
    }

    private fun mapLoadFailure(error: Throwable): NoteEditorUiState {
        val contentError = (error as? NoteContentException)?.error
        return when (contentError) {
            NoteContentError.InvalidNoteId -> NoteEditorUiState.InvalidNoteId
            NoteContentError.NotFound -> NoteEditorUiState.NotFound
            NoteContentError.InvalidWorkspacePath,
            NoteContentError.WorkspaceMissing,
            is NoteContentError.ReadFailed,
            null -> NoteEditorUiState.Error(LOAD_ERROR_MESSAGE)
        }
    }

    private fun mapSaveFailure(error: Throwable): String {
        val saveError = (error as? SaveNoteException)?.error
        return when (saveError) {
            SaveNoteError.ReadOnlyNote -> "This note is read-only."
            SaveNoteError.NotFound -> "This note is no longer in the workspace."
            SaveNoteError.InvalidNoteId -> "This note path is not valid."
            SaveNoteError.InvalidWorkspacePath,
            SaveNoteError.WorkspaceMissing,
            is SaveNoteError.WriteFailed,
            is SaveNoteError.RegistryRefreshFailed,
            null -> SAVE_ERROR_MESSAGE
        }
    }

    companion object {
        const val LOAD_ERROR_MESSAGE = "Could not open this note."
        const val SAVE_ERROR_MESSAGE = "Could not save this note."
        const val SAVED_MESSAGE = "Saved"

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId,
            loadEditableNote: LoadEditableNote,
            saveNote: SaveNote,
            loadGitStatusSummary: LoadGitStatusSummary
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteEditorViewModel(
                config,
                filesDir,
                noteId,
                loadEditableNote,
                saveNote,
                loadGitStatusSummary
            ) as T
        }
    }
}

class CreateInboxNoteViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val createInboxNote: CreateInboxNote
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateInboxUiState>(CreateInboxUiState.Creating)
    val uiState: StateFlow<CreateInboxUiState> = _uiState.asStateFlow()

    private val _createdNoteId = MutableStateFlow<NoteId?>(null)
    val createdNoteId: StateFlow<NoteId?> = _createdNoteId.asStateFlow()

    private var createJob: Job? = null

    init {
        create()
    }

    fun retry() {
        create()
    }

    private fun create() {
        createJob?.cancel()
        createJob = viewModelScope.launch {
            _uiState.value = CreateInboxUiState.Creating
            createInboxNote(config, filesDir).fold(
                onSuccess = { result ->
                    _createdNoteId.value = result.note.id
                },
                onFailure = { error ->
                    _uiState.value = CreateInboxUiState.Error(mapCreateFailure(error))
                }
            )
        }
    }

    private fun mapCreateFailure(error: Throwable): String {
        val createError = (error as? CreateNoteException)?.error
        return when (createError) {
            CreateNoteError.InvalidWorkspacePath,
            CreateNoteError.WorkspaceMissing,
            is CreateNoteError.WriteFailed,
            is CreateNoteError.RegistryRefreshFailed,
            is CreateNoteError.VerificationFailed,
            null -> CREATE_ERROR_MESSAGE
        }
    }

    companion object {
        const val CREATE_ERROR_MESSAGE = "Could not create inbox note."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            createInboxNote: CreateInboxNote
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateInboxNoteViewModel(config, filesDir, createInboxNote) as T
        }
    }
}
