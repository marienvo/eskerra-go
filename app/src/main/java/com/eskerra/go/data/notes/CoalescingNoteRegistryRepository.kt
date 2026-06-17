package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Deduplicates concurrent [refresh] calls for the same workspace so parallel callers
 * (e.g. inbox + Today Hub on home load) share one in-flight vault scan.
 */
class CoalescingNoteRegistryRepository(private val delegate: NoteRegistryRepository) :
    NoteRegistryRepository {

    private val inFlight = mutableMapOf<String, InFlightScan>()
    private val pendingRescan = mutableSetOf<String>()

    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry?
    ): Result<NoteRegistry> {
        val key = cacheKey(config, filesDir)
        while (true) {
            val joinTarget = synchronized(inFlight) { inFlight[key] }
            if (joinTarget != null) {
                markPendingRescanIfLateJoin(key, joinTarget)
                val result = joinTarget.deferred.await()
                if (synchronized(inFlight) { pendingRescan.remove(key) == true }) {
                    continue
                }
                return result
            }

            val owned = CompletableDeferred<Result<NoteRegistry>>()
            val scan = InFlightScan(deferred = owned, startedAtNanos = System.nanoTime())
            val becameOwner = synchronized(inFlight) {
                if (inFlight[key] != null) {
                    false
                } else {
                    inFlight[key] = scan
                    true
                }
            }
            if (!becameOwner) continue

            val result = try {
                withContext(NonCancellable) {
                    delegate.refresh(config, filesDir, previousRegistry)
                }
            } catch (e: CancellationException) {
                synchronized(inFlight) {
                    if (inFlight[key] === scan) inFlight.remove(key)
                }
                if (!owned.isCompleted) {
                    owned.cancel(e)
                }
                throw e
            } catch (e: Throwable) {
                synchronized(inFlight) {
                    if (inFlight[key] === scan) inFlight.remove(key)
                }
                owned.completeExceptionally(e)
                throw e
            }

            owned.complete(result)
            synchronized(inFlight) {
                if (inFlight[key] === scan) inFlight.remove(key)
            }

            if (synchronized(inFlight) { pendingRescan.remove(key) == true }) {
                continue
            }
            return result
        }
    }

    private fun markPendingRescanIfLateJoin(key: String, scan: InFlightScan) {
        val elapsedNanos = System.nanoTime() - scan.startedAtNanos
        if (elapsedNanos >= LATE_JOIN_THRESHOLD_NANOS) {
            synchronized(inFlight) { pendingRescan.add(key) }
        }
    }

    private fun cacheKey(config: WorkspaceConfig, filesDir: File): String =
        "${filesDir.path}:${config.relativePath}"

    private data class InFlightScan(
        val deferred: Deferred<Result<NoteRegistry>>,
        val startedAtNanos: Long
    )

    private companion object {
        private const val LATE_JOIN_THRESHOLD_NANOS = 50L * 1_000_000L
    }
}
