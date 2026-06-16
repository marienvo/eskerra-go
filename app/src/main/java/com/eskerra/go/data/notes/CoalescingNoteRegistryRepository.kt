package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * Deduplicates concurrent [refresh] calls for the same workspace so parallel callers
 * (e.g. inbox + Today Hub on home load) share one in-flight vault scan.
 */
class CoalescingNoteRegistryRepository(private val delegate: NoteRegistryRepository) :
    NoteRegistryRepository {

    private val inFlight = mutableMapOf<String, Deferred<Result<NoteRegistry>>>()

    override suspend fun refresh(config: WorkspaceConfig, filesDir: File): Result<NoteRegistry> {
        val key = cacheKey(config, filesDir)
        val existing: Deferred<Result<NoteRegistry>>?
        val owned = CompletableDeferred<Result<NoteRegistry>>()
        synchronized(inFlight) {
            existing = inFlight[key]
            if (existing == null) inFlight[key] = owned
        }
        existing?.let { return it.await() }

        return try {
            val result = delegate.refresh(config, filesDir)
            owned.complete(result)
            result
        } catch (e: CancellationException) {
            dropInFlight(key, owned)
            if (!owned.isCompleted) {
                owned.completeExceptionally(e)
            }
            throw e
        } catch (e: Throwable) {
            dropInFlight(key, owned)
            owned.completeExceptionally(e)
            throw e
        } finally {
            dropInFlight(key, owned)
        }
    }

    private fun cacheKey(config: WorkspaceConfig, filesDir: File): String =
        "${filesDir.path}:${config.relativePath}"

    private fun dropInFlight(key: String, owned: Deferred<Result<NoteRegistry>>) {
        synchronized(inFlight) { if (inFlight[key] === owned) inFlight.remove(key) }
    }
}
