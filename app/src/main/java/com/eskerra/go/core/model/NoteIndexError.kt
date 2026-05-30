package com.eskerra.go.core.model

/** Typed failures while scanning or indexing workspace notes. */
sealed interface NoteIndexError {
    data class InvalidWorkspacePath(val detail: String?) : NoteIndexError

    data class WorkspaceMissing(val detail: String?) : NoteIndexError

    data class ScanFailed(val detail: String?) : NoteIndexError
}

class NoteIndexException(val error: NoteIndexError) : Exception(error.toString())
