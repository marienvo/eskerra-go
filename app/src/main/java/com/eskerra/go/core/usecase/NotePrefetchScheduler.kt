package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * App-scoped background warmer for [WarmNote]: takes an ordered list of prefetch candidates from
 * whoever last called [submit] — the open note's linked notes (closest-to-viewport first), or the
 * Today Hub's visible links once launch has settled — and warms them at low, bounded concurrency.
 *
 * One process-wide instance is shared by every submitter (see [com.eskerra.go.MainActivity]), not
 * tied to a per-note ViewModel scope: earlier per-note prefetch jobs used to keep running in the
 * back stack and pile up, competing for CPU with whatever note is open now. Here, every [submit]
 * cancels the previous batch outright — "the newest note always wins" — so switching notes (or
 * scrolling to a different part of one) immediately reprioritizes; already-warm targets cost
 * nothing to re-submit since the underlying caches short-circuit on a hit.
 */
class NotePrefetchScheduler(
    private val warmNote: WarmNote,
    private val maxConcurrency: Int = DEFAULT_CONCURRENCY,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val gate = Semaphore(maxConcurrency)

    @Volatile
    private var currentBatch: Job? = null

    /**
     * Replaces any pending/in-flight batch with [orderedTargets]. A no-op for an empty list (the
     * previous batch is still cancelled, so a note with no links correctly stops prior prefetch).
     */
    fun submit(config: WorkspaceConfig, filesDir: File, orderedTargets: List<NoteId>) {
        currentBatch?.cancel()
        if (orderedTargets.isEmpty()) return
        currentBatch = scope.launch {
            orderedTargets.map { noteId ->
                async {
                    gate.withPermit { warmNote(config, filesDir, noteId) }
                }
            }.awaitAll()
        }
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 2
    }
}
