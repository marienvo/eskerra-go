package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2ConditionalResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistEtagPollerTest {

    private val entry = PlaylistEntry(
        episodeId = "e",
        mp3Url = "u",
        positionMs = 0,
        durationMs = 0,
        updatedAt = 1
    )

    private fun TestScope.poller(
        intervalMs: Long = 1000L,
        fetch: suspend (etag: String?) -> R2ConditionalResult,
        onDataChanged: (PlaylistEntry) -> Unit = {},
        onRemoteCleared: (() -> Unit)? = null,
        onTransientError: ((Throwable) -> Unit)? = null
    ) = PlaylistEtagPoller(
        initialIntervalMs = intervalMs,
        scope = this,
        fetchConditional = fetch,
        onDataChanged = onDataChanged,
        onRemotePlaylistCleared = onRemoteCleared,
        onTransientError = onTransientError
    )

    @Test
    fun `does not call onDataChanged for not_modified`() = runTest(StandardTestDispatcher()) {
        var called = false
        val p =
            poller(fetch = { R2ConditionalResult.NotModified }, onDataChanged = { called = true })
        p.setActive(true)
        runCurrent()
        assertEquals(false, called)
        p.dispose()
    }

    @Test
    fun `calls onDataChanged on updated and stores etag`() = runTest(StandardTestDispatcher()) {
        var received: PlaylistEntry? = null
        val p = poller(
            fetch = { R2ConditionalResult.Updated(entry, "\"x\"") },
            onDataChanged = { received = it }
        )
        p.setActive(true)
        runCurrent()
        assertEquals(entry, received)
        assertEquals("\"x\"", p.getEtag())
        p.dispose()
    }

    @Test
    fun `skips a tick while a fetch is in flight`() = runTest(StandardTestDispatcher()) {
        var fetchCount = 0
        val hang = CompletableDeferred<R2ConditionalResult>()
        val p = poller(
            intervalMs = 500,
            fetch = {
                fetchCount++
                hang.await()
            }
        )
        p.setActive(true)
        runCurrent() // immediate tick starts (in flight)
        assertEquals(1, fetchCount)

        advanceTimeBy(500) // timer fires → in flight, skip
        runCurrent()
        assertEquals(1, fetchCount)

        hang.complete(R2ConditionalResult.NotModified)
        runCurrent() // first tick finishes

        advanceTimeBy(500) // timer fires again → now idle
        runCurrent()
        assertEquals(2, fetchCount)
        p.dispose()
    }

    @Test
    fun `runs an immediate check when becoming active`() = runTest(StandardTestDispatcher()) {
        var fetchCount = 0
        val p = poller(
            intervalMs = 1000,
            fetch = {
                fetchCount++
                R2ConditionalResult.NotModified
            }
        )
        p.setActive(true)
        runCurrent()
        assertEquals(1, fetchCount)
        p.dispose()
    }

    @Test
    fun `stops polling and aborts when becoming inactive`() = runTest(StandardTestDispatcher()) {
        val hang = CompletableDeferred<R2ConditionalResult>()
        var fetchCount = 0
        val p = poller(fetch = {
            fetchCount++
            hang.await()
        })
        p.setActive(true)
        runCurrent()
        assertEquals(1, fetchCount)

        p.setActive(false)
        hang.complete(R2ConditionalResult.NotModified)
        runCurrent()

        // Timer should be cancelled — no extra calls
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(1, fetchCount)
        p.dispose()
    }

    @Test
    fun `clears etag on missing and calls onRemotePlaylistCleared once after updated`() =
        runTest(StandardTestDispatcher()) {
            var clearCount = 0
            var fetchCount = 0
            val results = listOf(
                R2ConditionalResult.Updated(entry, "\"a\""),
                R2ConditionalResult.Missing
            )
            val p = poller(
                intervalMs = 10,
                fetch = { results[fetchCount++] },
                onRemoteCleared = { clearCount++ }
            )
            p.setActive(true)
            runCurrent()
            assertEquals("\"a\"", p.getEtag())

            advanceTimeBy(10)
            runCurrent()
            assertNull(p.getEtag())
            assertEquals(1, clearCount)
            p.dispose()
        }

    @Test
    fun `does not call onRemotePlaylistCleared on first tick missing`() =
        runTest(StandardTestDispatcher()) {
            var clearCount = 0
            val p = poller(
                fetch = { R2ConditionalResult.Missing },
                onRemoteCleared = { clearCount++ }
            )
            p.setActive(true)
            runCurrent()
            assertEquals(0, clearCount)
            p.dispose()
        }

    @Test
    fun `calls onRemotePlaylistCleared once for updated then not_modified then missing`() =
        runTest(StandardTestDispatcher()) {
            var clearCount = 0
            var fetchCount = 0
            val results = listOf(
                R2ConditionalResult.Updated(entry, "\"a\""),
                R2ConditionalResult.NotModified,
                R2ConditionalResult.Missing
            )
            val p = poller(
                intervalMs = 60_000,
                fetch = { results[fetchCount++] },
                onRemoteCleared = { clearCount++ }
            )
            p.setActive(true)
            runCurrent()
            assertEquals(1, fetchCount)

            p.triggerCheck()
            runCurrent()
            assertEquals(2, fetchCount)

            p.triggerCheck()
            runCurrent()
            assertEquals(3, fetchCount)
            assertEquals(1, clearCount)
            p.dispose()
        }

    @Test
    fun `calls onRemotePlaylistCleared only once for updated then missing twice`() =
        runTest(StandardTestDispatcher()) {
            var clearCount = 0
            var fetchCount = 0
            val results = listOf(
                R2ConditionalResult.Updated(entry, "\"a\""),
                R2ConditionalResult.Missing,
                R2ConditionalResult.Missing
            )
            val p = poller(
                intervalMs = 60_000,
                fetch = { results[fetchCount++] },
                onRemoteCleared = { clearCount++ }
            )
            p.setActive(true)
            runCurrent()
            p.triggerCheck()
            runCurrent()
            assertEquals(1, clearCount)
            p.triggerCheck()
            runCurrent()
            assertEquals(1, clearCount)
            p.dispose()
        }

    @Test
    fun `notifies onTransientError for non-abort failures`() = runTest(StandardTestDispatcher()) {
        val boom = RuntimeException("network")
        var caught: Throwable? = null
        val p = poller(
            fetch = { throw boom },
            onTransientError = { caught = it }
        )
        p.setActive(true)
        runCurrent()
        assertEquals(boom, caught)
        p.dispose()
    }

    @Test
    fun `setIntervalMs reschedules without immediate tick and preserves etag`() =
        runTest(StandardTestDispatcher()) {
            var fetchCount = 0
            val results = mutableListOf<R2ConditionalResult>(
                R2ConditionalResult.Updated(entry, "\"etag1\""),
                R2ConditionalResult.NotModified,
                R2ConditionalResult.NotModified
            )
            val p = poller(
                intervalMs = 1000,
                fetch = { results[fetchCount++] }
            )
            p.setActive(true)
            runCurrent()
            assertEquals(1, fetchCount)
            assertEquals("\"etag1\"", p.getEtag())

            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, fetchCount)

            p.setIntervalMs(5000)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, fetchCount) // no extra tick on reschedule

            advanceTimeBy(4000)
            runCurrent()
            assertEquals(3, fetchCount)
            assertEquals("\"etag1\"", p.getEtag())
            p.dispose()
        }

    @Test
    fun `setIntervalMs while inactive applies when activated`() =
        runTest(StandardTestDispatcher()) {
            var fetchCount = 0
            val p = poller(
                intervalMs = 1000,
                fetch = {
                    fetchCount++
                    R2ConditionalResult.NotModified
                }
            )
            p.setIntervalMs(3000)
            p.setActive(true)
            runCurrent()
            assertEquals(1, fetchCount) // immediate tick

            advanceTimeBy(1000)
            runCurrent()
            assertEquals(1, fetchCount) // 1 s should not fire at 3 s interval

            advanceTimeBy(2000)
            runCurrent()
            assertEquals(2, fetchCount)
            p.dispose()
        }
}
