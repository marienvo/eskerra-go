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
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.data.perf.SnappyPerfLog
import java.io.File

/**
 * Refreshes the note registry, loads markdown for [noteId], and returns a reader document carrying
 * the content plus the registry. Wiki / internal link resolution happens in the renderer
 * ([com.eskerra.go.core.markdown.VaultReadonlyLink]); this use case no longer pre-computes segments.
 */
class LoadNoteForReading(
    private val registryRepository: NoteRegistryRepository,
    private val contentRepository: NoteContentRepository
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteReaderDocument> {
        val openStartNanos = System.nanoTime()
        if (NotePath.fromRelativePath(noteId.value).isFailure) {
            return Result.failure(NoteContentException(NoteContentError.InvalidNoteId))
        }

        val registryStartNanos = System.nanoTime()
        val registryResult = registryRepository.refresh(config, filesDir)
        val registryMs = SnappyPerfLog.elapsedMs(registryStartNanos)
        if (registryResult.isFailure) {
            return Result.failure(registryFailure(registryResult.exceptionOrNull()))
        }

        val registry = registryResult.getOrThrow()
        val summary = registry.notes.find { it.id == noteId }
            ?: return Result.failure(NoteContentException(NoteContentError.NotFound))

        val contentStartNanos = System.nanoTime()
        val contentResult = contentRepository.load(config, filesDir, noteId)
        val contentMs = SnappyPerfLog.elapsedMs(contentStartNanos)
        if (contentResult.isFailure) {
            return Result.failure(contentResult.exceptionOrNull()!!)
        }

        SnappyPerfLog.log(
            event = "note_open",
            durationMs = SnappyPerfLog.elapsedMs(openStartNanos),
            extras = mapOf(
                "noteId" to noteId.value,
                "registryMs" to registryMs,
                "contentMs" to contentMs
            )
        )
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
