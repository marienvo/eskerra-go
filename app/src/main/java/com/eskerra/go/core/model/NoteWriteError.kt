package com.eskerra.go.core.model

/** Typed failures while writing note content to the workspace. */
sealed interface NoteWriteError {
    data object InvalidWorkspacePath : NoteWriteError

    data object WorkspaceMissing : NoteWriteError

    data object InvalidNotePath : NoteWriteError

    data class WriteFailed(val detail: String?) : NoteWriteError
}

class NoteWriteException(val error: NoteWriteError) : Exception(error.toString())

/** Typed failures while saving an existing note. */
sealed interface SaveNoteError {
    data object InvalidNoteId : SaveNoteError

    data object NotFound : SaveNoteError

    data object ReadOnlyNote : SaveNoteError

    data object InvalidWorkspacePath : SaveNoteError

    data object WorkspaceMissing : SaveNoteError

    data class WriteFailed(val detail: String?) : SaveNoteError

    data class RegistryRefreshFailed(val detail: String?) : SaveNoteError
}

class SaveNoteException(val error: SaveNoteError) : Exception(error.toString())

/** Typed failures while creating a new inbox note. */
sealed interface CreateNoteError {
    data object InvalidWorkspacePath : CreateNoteError

    data object WorkspaceMissing : CreateNoteError

    data class WriteFailed(val detail: String?) : CreateNoteError

    data class RegistryRefreshFailed(val detail: String?) : CreateNoteError

    data class VerificationFailed(val detail: String?) : CreateNoteError
}

class CreateNoteException(val error: CreateNoteError) : Exception(error.toString())

/** Outcome of a successful note save. */
data class SaveNoteResult(val note: EditableNote, val gitStatus: GitStatusSummary)

/** Outcome of a successful inbox note creation. */
data class CreateInboxNoteResult(val note: EditableNote, val gitStatus: GitStatusSummary)
