package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.NoteRegistrySnapshotStore
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteRegistryCacheTest {

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = "vault",
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val filesDir = File("/tmp/does-not-matter")

    @Test
    fun current_returnsNull_whenNothingCachedAndNoSnapshot() = runTest {
        val cache = NoteRegistryCache(repositoryReturning(registryOf("A")))

        assertNull(cache.current(config, filesDir))
    }

    @Test
    fun current_doesNotScan() = runTest {
        val repository = RecordingRegistryRepository(listOf(Result.success(registryOf("A"))))
        val cache = NoteRegistryCache(repository)

        cache.current(config, filesDir)

        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun current_servesPersistedSnapshot_andPromotesToInMemory() = runTest {
        val snapshot = registryOf("Persisted")
        val store = FakeSnapshotStore(stored = snapshot)
        val cache = NoteRegistryCache(repositoryReturning(registryOf("Scanned")), store)

        val first = cache.current(config, filesDir)

        assertEquals(snapshot, first)
        assertEquals(snapshot, cache.registry.value)
        assertEquals(1, store.readCount)

        // Second read hits the lock-free in-memory path; no further snapshot reads.
        cache.current(config, filesDir)
        assertEquals(1, store.readCount)
    }

    @Test
    fun refresh_publishesResult_andPersistsSnapshot() = runTest {
        val fresh = registryOf("Fresh")
        val store = FakeSnapshotStore()
        val cache = NoteRegistryCache(repositoryReturning(fresh), store)

        val result = cache.refresh(config, filesDir)

        assertEquals(fresh, result.getOrThrow())
        assertEquals(fresh, cache.registry.value)
        assertEquals(fresh, store.stored)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun refresh_passesCachedRegistryAsMemoBase() = runTest {
        val first = registryOf("First")
        val second = registryOf("Second")
        val repository = RecordingRegistryRepository(
            listOf(Result.success(first), Result.success(second))
        )
        val cache = NoteRegistryCache(repository)

        cache.refresh(config, filesDir)
        cache.refresh(config, filesDir)

        assertEquals(listOf<NoteRegistry?>(null, first), repository.previousRegistries)
    }

    @Test
    fun refresh_failure_keepsPreviouslyCachedValue() = runTest {
        val good = registryOf("Good")
        val repository = RecordingRegistryRepository(
            listOf(Result.success(good), Result.failure(RuntimeException("boom")))
        )
        val store = FakeSnapshotStore()
        val cache = NoteRegistryCache(repository, store)

        cache.refresh(config, filesDir)
        val failed = cache.refresh(config, filesDir)

        assertTrue(failed.isFailure)
        // Stale-while-revalidate: the good registry survives a failed revalidation.
        assertEquals(good, cache.registry.value)
        assertEquals(good, cache.current(config, filesDir))
        assertEquals(1, store.saveCount)
    }

    @Test
    fun invalidate_dropsInMemoryAndSnapshot() = runTest {
        val store = FakeSnapshotStore()
        val cache = NoteRegistryCache(repositoryReturning(registryOf("A")), store)
        cache.refresh(config, filesDir)
        assertEquals(1, store.saveCount)

        cache.invalidate(config, filesDir)

        assertNull(cache.registry.value)
        assertNull(store.stored)
        assertEquals(1, store.clearCount)
        assertNull(cache.current(config, filesDir))
    }

    @Test
    fun refresh_concurrentCalls_areSerializedAndThreadPreviousRegistry() = runTest {
        val first = registryOf("First")
        val second = registryOf("Second")
        val repository = RecordingRegistryRepository(
            results = listOf(Result.success(first), Result.success(second)),
            delayMs = 50L
        )
        val cache = NoteRegistryCache(repository)

        launch { cache.refresh(config, filesDir) }
        launch { cache.refresh(config, filesDir) }
        advanceUntilIdle()

        assertEquals(2, repository.refreshCount)
        // Second refresh ran after the first published, so it sees `first` as its memo base.
        assertEquals(listOf<NoteRegistry?>(null, first), repository.previousRegistries)
        assertSame(second, cache.registry.value)
    }

    private fun registryOf(id: String): NoteRegistry = NoteRegistry.fromNotes(
        listOf(
            NoteSummary(
                id = NoteId("Inbox/$id.md"),
                title = id,
                snippet = "",
                isInbox = true
            )
        )
    )

    private fun repositoryReturning(registry: NoteRegistry): NoteRegistryRepository =
        RecordingRegistryRepository(listOf(Result.success(registry)))

    private class RecordingRegistryRepository(
        private val results: List<Result<NoteRegistry>>,
        private val delayMs: Long = 0L
    ) : NoteRegistryRepository {

        val previousRegistries = mutableListOf<NoteRegistry?>()
        var refreshCount = 0
            private set

        override suspend fun refresh(
            config: WorkspaceConfig,
            filesDir: File,
            previousRegistry: NoteRegistry?
        ): Result<NoteRegistry> {
            previousRegistries += previousRegistry
            val index = refreshCount.coerceAtMost(results.lastIndex)
            refreshCount += 1
            if (delayMs > 0L) {
                delay(delayMs)
            }
            return results[index]
        }
    }

    private class FakeSnapshotStore(var stored: NoteRegistry? = null) : NoteRegistrySnapshotStore {

        var readCount = 0
            private set
        var saveCount = 0
            private set
        var clearCount = 0
            private set

        override suspend fun read(config: WorkspaceConfig, filesDir: File): NoteRegistry? {
            readCount += 1
            return stored
        }

        override suspend fun save(config: WorkspaceConfig, filesDir: File, registry: NoteRegistry) {
            stored = registry
            saveCount += 1
        }

        override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
            stored = null
            clearCount += 1
        }
    }
}
