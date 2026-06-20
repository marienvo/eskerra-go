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
import com.eskerra.go.core.repository.NoteRegistryCachePort
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Loads the registry and markdown for [noteId] and returns a reader document.
 *
 * Registry strategy (SWR): if a registry is already cached, it is served immediately on the
 * critical path and an incremental [NoteRegistryCachePort.refresh] is dispatched on
 * [backgroundScope] so the next open benefits from an up-to-date index. On a cold miss the
 * critical path awaits [refresh] before continuing.
 *
 * Wiki / internal link resolution happens in the renderer
 * ([com.eskerra.go.core.markdown.VaultReadonlyLink]); this use case no longer pre-computes
 * segments.
 */
class LoadNoteForReading(
    private val registryCache: NoteRegistryCachePort,
    private val contentRepository: NoteContentRepository
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId,
        backgroundScope: CoroutineScope? = null
    ): Result<NoteReaderDocument> {
        if (NotePath.fromRelativePath(noteId.value).isFailure) {
            return Result.failure(NoteContentException(NoteContentError.InvalidNoteId))
        }

        val cachedRegistry = registryCache.current(config, filesDir)
        val registry = if (cachedRegistry != null) {
            backgroundScope?.launch { registryCache.refresh(config, filesDir) }
            cachedRegistry
        } else {
            val result = registryCache.refresh(config, filesDir)
            if (result.isFailure) return Result.failure(registryFailure(result.exceptionOrNull()))
            result.getOrThrow()
        }

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
