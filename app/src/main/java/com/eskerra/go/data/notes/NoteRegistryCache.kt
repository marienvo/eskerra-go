package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.NoteRegistrySnapshotStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-scoped, shared note registry with stale-while-revalidate semantics. One instance is
 * held by [com.eskerra.go.MainActivity] and consumed by every read path, so wiki-link navigation
 * and inbox/today reads no longer each re-walk the whole vault.
 *
 * - [current] serves the cached registry without scanning (in-memory → persisted snapshot → null).
 *   The in-memory hit path is lock-free, so reads stay instant even while a [refresh] is running.
 * - [refresh] incrementally rescans (reusing the cached registry as the memo base via
 *   [NoteRegistryRepository.refresh]), publishes to [registry], and persists a snapshot.
 * - [invalidate] marks the cache out of date after a known local mutation.
 *
 * A [Mutex] serializes refresh / cold-load / invalidate so concurrent callers never trigger
 * redundant full scans or torn snapshot writes. Externally-changed files (e.g. a git pull) only
 * surface after the next [refresh]; in the PoC there is no background sync, so callers must
 * [refresh] after manual sync.
 */
class NoteRegistryCache(
    private val repository: NoteRegistryRepository,
    private val snapshotStore: NoteRegistrySnapshotStore? = null
) {

    private val mutex = Mutex()
    private val _registry = MutableStateFlow<NoteRegistry?>(null)

    /** Shared in-memory registry; `null` until the first successful [current] or [refresh]. */
    val registry: StateFlow<NoteRegistry?> = _registry.asStateFlow()

    /**
     * Returns the cached registry without scanning: in-memory → persisted snapshot → `null`.
     * A persisted snapshot is promoted into memory so later reads hit the lock-free fast path.
     */
    suspend fun current(config: WorkspaceConfig, filesDir: File): NoteRegistry? {
        _registry.value?.let { return it }
        return mutex.withLock {
            _registry.value ?: snapshotStore?.read(config, filesDir)?.also { snapshot ->
                _registry.value = snapshot
            }
        }
    }

    /**
     * Incrementally rescans the workspace, publishes the result to [registry], and persists a
     * snapshot. A failed revalidation leaves the previously cached value intact (stale-while-
     * revalidate). Concurrent calls are serialized; the later one rescans against the now-fresh
     * registry, so it remains an incremental (cheap) pass.
     */
    suspend fun refresh(config: WorkspaceConfig, filesDir: File): Result<NoteRegistry> =
        mutex.withLock {
            repository.refresh(config, filesDir, _registry.value).onSuccess { fresh ->
                _registry.value = fresh
                snapshotStore?.save(config, filesDir, fresh)
            }
        }

    /**
     * Marks the cache out of date after a known local mutation: drops the in-memory registry (so
     * the next [refresh] does a full read) and the persisted snapshot. Callers should [refresh]
     * afterwards to repopulate.
     */
    suspend fun invalidate(config: WorkspaceConfig, filesDir: File) {
        mutex.withLock {
            _registry.value = null
            snapshotStore?.clear(config, filesDir)
        }
    }
}
