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
 * critical path. An incremental [NoteRegistryCachePort.refresh] is dispatched on [backgroundScope],
 * throttled to at most once per [refreshThrottleMs] — every note tap would otherwise re-trigger a
 * full vault walk that competes for IO/CPU with the note actually being opened, even though sync,
 * foreground return, and writes already keep the registry current. On a cold miss the critical path
 * always awaits [refresh] before continuing (there is nothing to throttle against yet).
 *
 * Wiki / internal link resolution happens in the renderer
 * ([com.eskerra.go.core.markdown.VaultReadonlyLink]); this use case no longer pre-computes
 * segments.
 */
class LoadNoteForReading(
    private val registryCache: NoteRegistryCachePort,
    private val contentRepository: NoteContentRepository,
    private val refreshThrottleMs: Long = DEFAULT_REFRESH_THROTTLE_MS,
    private val now: () -> Long = System::currentTimeMillis
) {

    // null means "never dispatched yet"; keeping it nullable (rather than a Long.MIN_VALUE
    // sentinel) avoids signed overflow in the elapsed-time subtraction below.
    @Volatile
    private var lastRefreshDispatchedAtMs: Long? = null

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
            val nowMs = now()
            val last = lastRefreshDispatchedAtMs
            val throttleElapsed = last == null || nowMs - last >= refreshThrottleMs
            if (backgroundScope != null && throttleElapsed) {
                lastRefreshDispatchedAtMs = nowMs
                backgroundScope.launch { registryCache.refresh(config, filesDir) }
            }
            cachedRegistry
        } else {
            val result = registryCache.refresh(config, filesDir)
            if (result.isFailure) return Result.failure(registryFailure(result.exceptionOrNull()))
            // The cold path just read a fresh registry synchronously; count it as a dispatch so an
            // immediately-following open does not redundantly re-trigger the background refresh.
            lastRefreshDispatchedAtMs = now()
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

    companion object {
        const val DEFAULT_REFRESH_THROTTLE_MS = 30_000L
    }
}
