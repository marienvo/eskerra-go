package com.eskerra.go.core.usecase

import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.ParsedMarkdownCache
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePrefetchSchedulerTest {

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = "vault",
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )
    private val filesDir = File("/tmp/does-not-matter")

    private fun noteId(name: String) = NoteId("Notes/$name.md")

    /** Records start/completion per note and can gate completion behind a [CompletableDeferred]. */
    private class RecordingRepository(private val gate: CompletableDeferred<Unit>? = null) :
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
                gate?.await()
                val path = NotePath.fromRelativePath(noteId.value).getOrThrow()
                completed += noteId
                return Result.success(NoteContent(noteId, path, "body:${noteId.value}"))
            } finally {
                concurrent -= 1
            }
        }
    }

    private fun scheduler(
        repository: NoteContentRepository,
        maxConcurrency: Int,
        testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler
    ): NotePrefetchScheduler {
        val warmNote = WarmNote(
            contentCache = NoteContentCache(repository),
            parsedMarkdownCache = ParsedMarkdownCache(prepare = { PreparedMarkdown(emptyList()) })
        )
        return NotePrefetchScheduler(
            warmNote = warmNote,
            maxConcurrency = maxConcurrency,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
    }

    @Test
    fun submit_respectsConcurrencyLimit() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = RecordingRepository(gate)
        val notePrefetchScheduler =
            scheduler(repo, maxConcurrency = 2, testScheduler = testScheduler)

        notePrefetchScheduler.submit(
            config,
            filesDir,
            listOf(noteId("A"), noteId("B"), noteId("C"), noteId("D"))
        )
        advanceUntilIdle() // all four try to start; only 2 permits exist
        assertEquals(2, repo.started.size)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(4, repo.started.size)
        assertEquals(2, repo.maxConcurrent)
    }

    @Test
    fun submit_ordersWarmingByGivenOrder() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = RecordingRepository(gate)
        val notePrefetchScheduler =
            scheduler(repo, maxConcurrency = 1, testScheduler = testScheduler)

        notePrefetchScheduler.submit(config, filesDir, listOf(noteId("First"), noteId("Second")))
        advanceUntilIdle() // only one permit: exactly the first target starts

        assertEquals(listOf(noteId("First")), repo.started)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(noteId("First"), noteId("Second")), repo.started)
    }

    @Test
    fun submit_cancelsPreviousBatch_newestWins() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = RecordingRepository(gate)
        val notePrefetchScheduler =
            scheduler(repo, maxConcurrency = 1, testScheduler = testScheduler)

        notePrefetchScheduler.submit(config, filesDir, listOf(noteId("Stale"), noteId("AlsoStale")))
        advanceUntilIdle() // "Stale" is in-flight, gated on `gate`

        notePrefetchScheduler.submit(config, filesDir, listOf(noteId("Fresh")))
        gate.complete(Unit)
        advanceUntilIdle()

        // "AlsoStale" never starts: the batch it belonged to was cancelled before its turn.
        assertTrue(noteId("Fresh") in repo.started)
        assertTrue(noteId("AlsoStale") !in repo.started)
    }

    @Test
    fun submit_resubmittingSameTarget_doesNotReloadContent() = runTest {
        val repo = RecordingRepository()
        val notePrefetchScheduler =
            scheduler(repo, maxConcurrency = 2, testScheduler = testScheduler)

        notePrefetchScheduler.submit(config, filesDir, listOf(noteId("Warm")))
        advanceUntilIdle()
        notePrefetchScheduler.submit(config, filesDir, listOf(noteId("Warm")))
        advanceUntilIdle()

        assertEquals(1, repo.started.size)
    }
}
