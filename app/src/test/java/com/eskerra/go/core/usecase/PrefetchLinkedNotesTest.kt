package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.notes.NoteContentCache
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchLinkedNotesTest {

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = "vault",
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val filesDir = File("/tmp/does-not-matter")

    private val source = note("Notes/Source.md", "Source")

    @Test
    fun loadsAllResolvedTargets() = runTest {
        val t1 = note("Notes/T1.md", "T1")
        val t2 = note("Notes/T2.md", "T2")
        val repo = RecordingContentRepository()
        val useCase = PrefetchLinkedNotes(
            contentCache = NoteContentCache(repo),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase(config, filesDir, document("[[T1]] and [[T2]]", source, t1, t2))
        advanceUntilIdle()

        assertEquals(setOf(t1.id, t2.id), repo.completed.toSet())
    }

    @Test
    fun emptyTargets_doesNothing() = runTest {
        val repo = RecordingContentRepository()
        val useCase = PrefetchLinkedNotes(
            contentCache = NoteContentCache(repo),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase(config, filesDir, document("No links here.", source))
        advanceUntilIdle()

        assertTrue(repo.started.isEmpty())
    }

    @Test
    fun boundsConcurrency() = runTest {
        val targets = (1..5).map { note("Notes/T$it.md", "T$it") }
        val repo = RecordingContentRepository(delayMs = 50L)
        val useCase = PrefetchLinkedNotes(
            contentCache = NoteContentCache(repo),
            maxConcurrency = 2,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val markdown = targets.joinToString(" ") { "[[${it.title}]]" }

        useCase(config, filesDir, document(markdown, source, *targets.toTypedArray()))
        advanceUntilIdle()

        assertEquals(5, repo.completed.size)
        assertEquals(2, repo.maxConcurrent)
    }

    @Test
    fun cancellation_stopsRemainingPrefetch() = runTest {
        val targets = (1..4).map { note("Notes/T$it.md", "T$it") }
        val repo = RecordingContentRepository(delayMs = 100L)
        val useCase = PrefetchLinkedNotes(
            contentCache = NoteContentCache(repo),
            maxConcurrency = 1,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val markdown = targets.joinToString(" ") { "[[${it.title}]]" }

        val job = launch {
            useCase(config, filesDir, document(markdown, source, *targets.toTypedArray()))
        }
        advanceTimeBy(150L) // T1 completes (100ms); T2 still in flight
        job.cancel()
        advanceUntilIdle()

        assertTrue("expected fewer than all loads", repo.completed.size < targets.size)
    }

    @Test
    fun warmsCacheForInstantSubsequentLoad() = runTest {
        val target = note("Notes/Target.md", "Target")
        val repo = RecordingContentRepository()
        val cache = NoteContentCache(repo)
        val useCase = PrefetchLinkedNotes(
            contentCache = cache,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        useCase(config, filesDir, document("[[Target]]", source, target))
        advanceUntilIdle()
        assertEquals(1, repo.started.size)

        // A later open of the prefetched note hits the warm cache; the delegate is not called again.
        cache.load(config, filesDir, target.id)
        assertEquals(1, repo.started.size)
    }

    private fun note(path: String, title: String) =
        NoteSummary(id = NoteId(path), title = title, snippet = "", isInbox = false)

    private fun document(
        markdown: String,
        source: NoteSummary,
        vararg others: NoteSummary
    ): NoteReaderDocument {
        val path = NotePath.fromRelativePath(source.id.value).getOrThrow()
        return NoteReaderDocument(
            note = source,
            content = NoteContent(source.id, path, markdown),
            registry = NoteRegistry.fromNotes(listOf(source) + others)
        )
    }

    private class RecordingContentRepository(private val delayMs: Long = 0L) :
        NoteContentRepository {

        val started = mutableListOf<NoteId>()
        val completed = mutableListOf<NoteId>()
        private var concurrent = 0
        var maxConcurrent = 0
            private set

        override suspend fun load(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId
        ): Result<NoteContent> {
            concurrent += 1
            maxConcurrent = max(maxConcurrent, concurrent)
            started += noteId
            try {
                if (delayMs > 0L) delay(delayMs)
                val path = NotePath.fromRelativePath(noteId.value).getOrThrow()
                completed += noteId
                return Result.success(NoteContent(noteId, path, "body:${noteId.value}"))
            } finally {
                concurrent -= 1
            }
        }
    }
}
