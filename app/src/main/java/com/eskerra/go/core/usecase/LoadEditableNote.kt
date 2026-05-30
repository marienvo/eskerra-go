package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EditableNote
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File

/**
 * Loads note content for the editor and derives editability from refreshed
 * registry metadata.
 */
class LoadEditableNote(
    private val registryRepository: NoteRegistryRepository,
    private val contentRepository: NoteContentRepository
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<EditableNote> {
        if (NotePath.fromRelativePath(noteId.value).isFailure) {
            return Result.failure(NoteContentException(NoteContentError.InvalidNoteId))
        }

        val registryResult = registryRepository.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(registryFailure(registryResult.exceptionOrNull()))
        }

        val registry = registryResult.getOrThrow()
        val summary = registry.notes.find { it.id == noteId }
            ?: return Result.failure(NoteContentException(NoteContentError.NotFound))

        val contentResult = contentRepository.load(config, filesDir, noteId)
        if (contentResult.isFailure) {
            return Result.failure(contentResult.exceptionOrNull()!!)
        }

        val content = contentResult.getOrThrow()
        return Result.success(
            EditableNote(
                id = summary.id,
                path = content.path,
                title = summary.title,
                markdown = content.markdown,
                isInbox = summary.isInbox,
                canEdit = summary.isInbox
            )
        )
    }

    internal companion object {
        private fun registryFailure(cause: Throwable?): NoteContentException {
            if (cause is NoteIndexException) {
                val error = when (cause.error) {
                    is NoteIndexError.InvalidWorkspacePath ->
                        NoteContentError.InvalidWorkspacePath
                    is NoteIndexError.WorkspaceMissing ->
                        NoteContentError.WorkspaceMissing
                    is NoteIndexError.ScanFailed ->
                        NoteContentError.ReadFailed(cause.error.detail)
                }
                return NoteContentException(error)
            }
            return NoteContentException(NoteContentError.ReadFailed(cause?.message))
        }
    }
}
