package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContentCacheTest {

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = "vault",
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val config2 = WorkspaceConfig(
        name = "My Notes",
        relativePath = "vault",
        remoteUri = null,
        branch = "feature/other",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val filesDir = File("/tmp/does-not-matter")

    private fun noteId(name: String) = NoteId("Inbox/$name.md")
    private fun repo(vararg pairs: Pair<NoteId, String>) = MultiNoteRepository(mapOf(*pairs))

    @Test
    fun load_miss_delegatesAndCachesResult() = runTest {
        val id = noteId("A")
        val repo = repo(id to "# A")
        val cache = NoteContentCache(repo)

        val result = cache.load(config, filesDir, id)

        assertEquals(1, repo.callCount)
        assertEquals("# A", result.getOrThrow().markdown)
    }

    @Test
    fun load_hit_doesNotCallDelegate() = runTest {
        val id = noteId("A")
        val repo = repo(id to "# A")
        val cache = NoteContentCache(repo)

        cache.load(config, filesDir, id)
        val second = cache.load(config, filesDir, id)

        assertEquals(1, repo.callCount)
        assertEquals("# A", second.getOrThrow().markdown)
    }

    @Test
    fun load_failure_isNotCached() = runTest {
        val repo = MultiNoteRepository(emptyMap())
        val cache = NoteContentCache(repo)

        val first = cache.load(config, filesDir, noteId("Missing"))
        val second = cache.load(config, filesDir, noteId("Missing"))

        assertTrue(first.isFailure)
        assertTrue(second.isFailure)
        assertEquals(2, repo.callCount)
    }

    @Test
    fun load_exceedsCapacity_evictsLruEntry() = runTest {
        val a = noteId("A")
        val b = noteId("B")
        val c = noteId("C")
        val repo = repo(a to "A", b to "B", c to "C")
        val cache = NoteContentCache(repo, maxSize = 2)

        cache.load(config, filesDir, a) // lru: [A]
        cache.load(config, filesDir, b) // lru: [A(LRU), B]
        cache.load(config, filesDir, c) // lru: [B, C]; A evicted

        val countAfterPopulate = repo.callCount // 3
        cache.load(config, filesDir, b) // hit
        cache.load(config, filesDir, a) // miss — A was evicted

        assertEquals(countAfterPopulate + 1, repo.callCount)
    }

    @Test
    fun load_lruAccess_updatesOrder() = runTest {
        val a = noteId("A")
        val b = noteId("B")
        val c = noteId("C")
        val repo = repo(a to "A", b to "B", c to "C")
        val cache = NoteContentCache(repo, maxSize = 2)

        cache.load(config, filesDir, a) // lru: [A]
        cache.load(config, filesDir, b) // lru: [A(LRU), B]
        cache.load(config, filesDir, a) // hit; A moves to MRU: lru: [B(LRU), A]
        cache.load(config, filesDir, c) // miss; lru: [A, C]; B evicted

        val countAfterPopulate = repo.callCount // 3 (A, B, C each loaded once)
        cache.load(config, filesDir, a) // hit — A is still cached
        cache.load(config, filesDir, b) // miss — B was the LRU entry evicted above

        assertEquals(countAfterPopulate + 1, repo.callCount)
    }

    @Test
    fun evict_removesEntryFromCache() = runTest {
        val id = noteId("A")
        val repo = repo(id to "# A")
        val cache = NoteContentCache(repo)

        cache.load(config, filesDir, id)
        cache.evict(id)
        cache.load(config, filesDir, id)

        assertEquals(2, repo.callCount)
    }

    @Test
    fun evictAll_clearsAllEntries() = runTest {
        val a = noteId("A")
        val b = noteId("B")
        val repo = repo(a to "A", b to "B")
        val cache = NoteContentCache(repo)

        cache.load(config, filesDir, a)
        cache.load(config, filesDir, b)
        cache.evictAll()
        cache.load(config, filesDir, a)
        cache.load(config, filesDir, b)

        assertEquals(4, repo.callCount)
    }

    @Test
    fun load_fingerprintChange_clearsCacheAndDelegatesAgain() = runTest {
        val id = noteId("A")
        val repo = repo(id to "# A")
        val cache = NoteContentCache(repo)

        cache.load(config, filesDir, id) // miss → delegate; fingerprint = FP1
        cache.load(config, filesDir, id) // hit
        assertEquals(1, repo.callCount)

        cache.load(config2, filesDir, id) // fingerprint changed → clear → delegate
        assertEquals(2, repo.callCount)

        cache.load(config2, filesDir, id) // hit under FP2
        assertEquals(2, repo.callCount)
    }

    private class MultiNoteRepository(private val notes: Map<NoteId, String>) :
        NoteContentRepository {

        var callCount = 0
            private set

        override suspend fun load(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId
        ): Result<NoteContent> {
            callCount++
            val markdown = notes[noteId]
                ?: return Result.failure(NoteContentException(NoteContentError.NotFound))
            val path = NotePath.fromRelativePath(noteId.value).getOrThrow()
            return Result.success(NoteContent(noteId, path, markdown))
        }
    }
}
