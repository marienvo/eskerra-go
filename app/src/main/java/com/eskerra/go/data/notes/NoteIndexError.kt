package com.eskerra.go.data.notes

/** Typed failures while scanning or indexing workspace notes. */
sealed class NoteIndexError {
    abstract val detail: String?

    data class InvalidWorkspacePath(override val detail: String?) : NoteIndexError()

    data class WorkspaceMissing(override val detail: String?) : NoteIndexError()

    data class ScanFailed(override val detail: String?) : NoteIndexError()
}

class NoteIndexException(val error: NoteIndexError) : Exception(error.detail)
