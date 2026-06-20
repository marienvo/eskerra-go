package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File

/**
 * Refreshes the note registry, loads markdown for [noteId], and returns a reader document carrying
 * the content plus the registry. Wiki / internal link resolution happens in the renderer
 * ([com.eskerra.go.core.markdown.VaultReadonlyLink]); this use case no longer pre-computes segments.
 */
class LoadNoteForReading(
    private val registryCache: NoteRegistryCache,
    private val contentRepository: NoteContentRepository
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteReaderDocument> {
        if (NotePath.fromRelativePath(noteId.value).isFailure) {
            return Result.failure(NoteContentException(NoteContentError.InvalidNoteId))
        }

        val registryResult = registryCache.refresh(config, filesDir)
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

        return Result.success(
            NoteReaderDocument(
                note = summary,
                content = contentResult.getOrThrow(),
                registry = registry
            )
        )
    }

    private fun registryFailure(cause: Throwable?): NoteContentException {
        if (cause is NoteIndexException) {
            val error = when (cause.error) {
                is NoteIndexError.InvalidWorkspacePath -> NoteContentError.InvalidWorkspacePath
                is NoteIndexError.WorkspaceMissing -> NoteContentError.WorkspaceMissing
                is NoteIndexError.ScanFailed -> NoteContentError.ReadFailed(cause.error.detail)
            }
            return NoteContentException(error)
        }
        return NoteContentException(NoteContentError.ReadFailed(cause?.message))
    }
}
