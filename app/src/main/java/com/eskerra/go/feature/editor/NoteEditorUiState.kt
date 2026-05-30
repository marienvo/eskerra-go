package com.eskerra.go.feature.editor

import com.eskerra.go.core.model.EditableNote
import com.eskerra.go.core.model.GitStatusSummary

sealed interface NoteEditorUiState {
    data object Loading : NoteEditorUiState

    data class Content(
        val note: EditableNote,
        val draftMarkdown: String,
        val isDirty: Boolean,
        val isSaving: Boolean,
        val saveMessage: String?,
        val errorMessage: String?,
        val gitStatus: GitStatusSummary
    ) : NoteEditorUiState

    data object NotFound : NoteEditorUiState

    data object InvalidNoteId : NoteEditorUiState

    data class Error(val message: String) : NoteEditorUiState
}

sealed interface CreateInboxUiState {
    data object Creating : CreateInboxUiState

    data class Error(val message: String) : CreateInboxUiState
}
