package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bounded LRU in-memory cache over a [NoteContentRepository]. Cache misses delegate to [delegate]
 * and store the result; hits skip disk I/O entirely.
 *
 * Thread-safe: a [Mutex] serializes all state mutations. The mutex is NOT held during the
 * [delegate] call, so concurrent loads of different notes proceed in parallel.
 *
 * Call [evict] after writing a single note and [evictAll] after bulk external changes (e.g. a
 * git pull) to prevent stale reads.
 */
class NoteContentCache(
    private val delegate: NoteContentRepository,
    private val maxSize: Int = DEFAULT_SIZE
) : NoteContentRepository {

    private val mutex = Mutex()
    private val lru = LinkedHashMap<NoteId, NoteContent>(maxSize * 2, 0.75f, true)

    override suspend fun load(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteContent> {
        mutex.withLock { lru[noteId] }?.let { return Result.success(it) }
        return delegate.load(config, filesDir, noteId).onSuccess { content ->
            mutex.withLock {
                lru[noteId] = content
                while (lru.size > maxSize) {
                    lru.remove(lru.keys.first())
                }
            }
        }
    }

    suspend fun evict(noteId: NoteId) {
        mutex.withLock { lru.remove(noteId) }
    }

    suspend fun evictAll() {
        mutex.withLock { lru.clear() }
    }

    companion object {
        const val DEFAULT_SIZE = 16
    }
}
