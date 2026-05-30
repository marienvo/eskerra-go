package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EditableNote
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.SaveNoteError
import com.eskerra.go.core.model.SaveNoteException
import com.eskerra.go.core.model.SaveNoteResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.NoteWriteRepository
import java.io.File

/** Validates editability, writes markdown, and refreshes registry and Git status. */
class SaveNote(
    private val writeRepository: NoteWriteRepository,
    private val registryRepository: NoteRegistryRepository,
    private val loadGitStatusSummary: LoadGitStatusSummary
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId,
        markdown: String
    ): Result<SaveNoteResult> {
        val notePath = NotePath.fromRelativePath(noteId.value).getOrElse {
            return Result.failure(SaveNoteException(SaveNoteError.InvalidNoteId))
        }

        val registryResult = registryRepository.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(
                SaveNoteException(
                    SaveNoteError.RegistryRefreshFailed(
                        registryResult.exceptionOrNull()?.message
                    )
                )
            )
        }

        val registry = registryResult.getOrThrow()
        val summary = registry.notes.find { it.id == noteId }
            ?: return Result.failure(SaveNoteException(SaveNoteError.NotFound))

        if (!summary.isInbox) {
            return Result.failure(SaveNoteException(SaveNoteError.ReadOnlyNote))
        }

        writeRepository.write(config, filesDir, notePath, markdown).getOrElse { error ->
            return Result.failure(mapWriteFailure(error))
        }

        val refreshedRegistry = registryRepository.refresh(config, filesDir)
        if (refreshedRegistry.isFailure) {
            return Result.failure(
                SaveNoteException(
                    SaveNoteError.RegistryRefreshFailed(
                        refreshedRegistry.exceptionOrNull()?.message
                    )
                )
            )
        }

        val refreshedSummary = refreshedRegistry.getOrThrow().notes.find { it.id == noteId }
            ?: return Result.failure(SaveNoteException(SaveNoteError.NotFound))

        val gitStatus = loadGitStatusSummary(config, filesDir)
        return Result.success(
            SaveNoteResult(
                note = EditableNote(
                    id = refreshedSummary.id,
                    path = notePath,
                    title = refreshedSummary.title,
                    markdown = markdown,
                    isInbox = refreshedSummary.isInbox,
                    canEdit = refreshedSummary.isInbox
                ),
                gitStatus = gitStatus
            )
        )
    }

    internal companion object {
        private fun mapWriteFailure(error: Throwable): SaveNoteException {
            if (error is NoteWriteException) {
                val mapped = when (error.error) {
                    NoteWriteError.InvalidWorkspacePath ->
                        SaveNoteError.InvalidWorkspacePath
                    NoteWriteError.WorkspaceMissing ->
                        SaveNoteError.WorkspaceMissing
                    NoteWriteError.InvalidNotePath ->
                        SaveNoteError.InvalidNoteId
                    is NoteWriteError.WriteFailed ->
                        SaveNoteError.WriteFailed(error.error.detail)
                }
                return SaveNoteException(mapped)
            }
            return SaveNoteException(SaveNoteError.WriteFailed(error.message))
        }
    }
}
