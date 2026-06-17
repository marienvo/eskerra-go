package com.eskerra.go.core.usecase

import com.eskerra.go.core.markdown.PrefetchLinkTargets
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.NoteContentCache
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Warms the [NoteContentCache] with the notes linked from the open note, so a wiki-link tap reads
 * from memory instead of disk. The registry is already cached, so resolution is in-memory; only the
 * link targets' content is read.
 *
 * - **Lifecycle / cancel:** runs inside the caller's coroutine (the reader ViewModel scope).
 *   Cancelling that scope — e.g. when the reader is disposed or the user navigates on — cancels
 *   every in-flight prefetch through structured concurrency ([coroutineScope] + [withContext]).
 * - **Off the main thread:** all reads run on [dispatcher] (IO).
 * - **Bounded concurrency:** at most [maxConcurrency] reads run at once, so a densely linked note
 *   cannot flood the IO pool or starve a real on-tap load.
 * - **Best effort:** per-target failures are ignored (`load` returns a `Result`); a missing or
 *   locked file must never surface in the reader.
 */
class PrefetchLinkedNotes(
    private val contentCache: NoteContentCache,
    private val maxConcurrency: Int = DEFAULT_CONCURRENCY,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        document: NoteReaderDocument
    ) {
        val targets = PrefetchLinkTargets.resolve(
            markdown = document.content.markdown,
            sourceNoteId = document.note.id,
            registry = document.registry
        )
        if (targets.isEmpty()) return

        withContext(dispatcher) {
            val gate = Semaphore(maxConcurrency)
            coroutineScope {
                targets.map { noteId ->
                    async {
                        gate.withPermit {
                            contentCache.load(config, filesDir, noteId)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 4
    }
}
