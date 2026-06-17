package com.eskerra.go.core.usecase

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.DeleteInboxNoteError
import com.eskerra.go.core.model.DeleteInboxNoteException
import com.eskerra.go.core.model.DeleteInboxNotesResult
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File

/** Deletes inbox notes after inbox-only and stale-entry guards. */
class DeleteInboxNotes(
    private val writeRepository: NoteWriteRepository,
    private val registryCache: NoteRegistryCache,
    private val loadGitStatusSummary: LoadGitStatusSummary
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteIds: List<NoteId>,
        availableInboxNotes: List<NoteSummary>
    ): Result<DeleteInboxNotesResult> {
        if (noteIds.isEmpty()) {
            return Result.success(DeleteInboxNotesResult(loadGitStatusSummary(config, filesDir)))
        }

        val canonicalNotes = noteIds.map { noteId ->
            InboxNotePath.resolveCanonicalDeleteNote(noteId, availableInboxNotes)
        }
        if (canonicalNotes.any { it == null }) {
            return Result.failure(DeleteInboxNoteException(DeleteInboxNoteError.StaleEntry))
        }

        canonicalNotes.filterNotNull().forEach { note ->
            if (!InboxNotePath.isInboxRelativePath(note.id.value)) {
                return Result.failure(DeleteInboxNoteException(DeleteInboxNoteError.NotInInbox))
            }
        }

        canonicalNotes.filterNotNull().forEach { note ->
            val notePath = NotePath.fromRelativePath(note.id.value).getOrElse {
                return Result.failure(
                    DeleteInboxNoteException(DeleteInboxNoteError.DeleteFailed(it.message))
                )
            }
            writeRepository.delete(config, filesDir, notePath).getOrElse { error ->
                return Result.failure(mapDeleteFailure(error))
            }
        }

        registryCache.invalidate(config, filesDir)
        val registryResult = registryCache.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(
                DeleteInboxNoteException(
                    DeleteInboxNoteError.RegistryRefreshFailed(
                        registryResult.exceptionOrNull()?.message
                    )
                )
            )
        }

        val gitStatus = loadGitStatusSummary(config, filesDir)
        return Result.success(DeleteInboxNotesResult(gitStatus = gitStatus))
    }

    internal companion object {
        const val NOT_IN_INBOX_MESSAGE =
            "Could not verify that the selected entry belongs to Log."
        const val STALE_ENTRY_MESSAGE =
            "Could not delete selected entries because one or more entries are no longer " +
                "available. Refresh Vault and try again."

        private fun mapDeleteFailure(error: Throwable): DeleteInboxNoteException {
            if (error is NoteWriteException) {
                val mapped = when (error.error) {
                    NoteWriteError.InvalidWorkspacePath ->
                        DeleteInboxNoteError.InvalidWorkspacePath
                    NoteWriteError.WorkspaceMissing ->
                        DeleteInboxNoteError.WorkspaceMissing
                    NoteWriteError.InvalidNotePath,
                    is NoteWriteError.WriteFailed,
                    is NoteWriteError.DeleteFailed ->
                        DeleteInboxNoteError.DeleteFailed(
                            (error.error as? NoteWriteError.DeleteFailed)?.detail
                                ?: (error.error as? NoteWriteError.WriteFailed)?.detail
                        )
                }
                return DeleteInboxNoteException(mapped)
            }
            return DeleteInboxNoteException(DeleteInboxNoteError.DeleteFailed(error.message))
        }
    }
}
