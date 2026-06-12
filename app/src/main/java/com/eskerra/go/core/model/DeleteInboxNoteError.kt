package com.eskerra.go.core.model

sealed interface DeleteInboxNoteError {
    data object NotInInbox : DeleteInboxNoteError

    data object StaleEntry : DeleteInboxNoteError

    data object InvalidWorkspacePath : DeleteInboxNoteError

    data object WorkspaceMissing : DeleteInboxNoteError

    data class DeleteFailed(val detail: String?) : DeleteInboxNoteError

    data class RegistryRefreshFailed(val detail: String?) : DeleteInboxNoteError
}

class DeleteInboxNoteException(val error: DeleteInboxNoteError) : Exception(error.toString())

data class DeleteInboxNotesResult(val gitStatus: GitStatusSummary)
