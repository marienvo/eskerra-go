package com.eskerra.go.core.model

/** Typed failures while loading note content from the workspace. */
sealed interface NoteContentError {
    data object InvalidWorkspacePath : NoteContentError

    data object WorkspaceMissing : NoteContentError

    data object InvalidNoteId : NoteContentError

    data object NotFound : NoteContentError

    data class ReadFailed(val detail: String?) : NoteContentError
}

class NoteContentException(val error: NoteContentError) : Exception(error.toString())
