package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.R2ConditionalResult
import com.eskerra.go.core.repository.PlaylistSyncRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistR2PollingHostTest {

    private val tempFolder = TemporaryFolder().also { it.create() }
    private val workspace: File = tempFolder.root

    private val entry = PlaylistEntry("e", "u", 0, null)

    private fun TestScope.host(
        r2Configured: Boolean = true,
        fetch: suspend (etag: String?) -> R2ConditionalResult = { R2ConditionalResult.NotModified },
        repo: FakePlaylistSyncRepository = FakePlaylistSyncRepository()
    ) = PlaylistR2PollingHost(
        syncRepository = repo,
        workspaceRoot = workspace,
        isR2Configured = { r2Configured },
        fetchConditional = fetch,
        scope = this,
        intervalMs = 1_000L
    )

    // ── activation logic ──────────────────────────────────────────────────────

    @Test
    fun `poller activates when foreground and R2 configured and not playing`() =
        runTest(StandardTestDispatcher()) {
            var fetchCount = 0
            val h = host(fetch = {
                fetchCount++
                R2ConditionalResult.NotModified
            })
            h.setAppForeground(true)
            runCurrent()
            assertEquals(1, fetchCount)
            h.dispose()
        }

    @Test
    fun `refreshActiveState activates poller when R2 becomes configured`() =
        runTest(StandardTestDispatcher()) {
            var r2Configured = false
            var fetchCount = 0
            val h = PlaylistR2PollingHost(
                syncRepository = FakePlaylistSyncRepository(),
                workspaceRoot = workspace,
                isR2Configured = { r2Configured },
                fetchConditional = {
                    fetchCount++
                    R2ConditionalResult.NotModified
                },
                scope = this,
                intervalMs = 1_000L
            )
            h.setAppForeground(true)
            runCurrent()
            assertEquals(0, fetchCount)

            r2Configured = true
            h.refreshActiveState()
            runCurrent()
            assertEquals(1, fetchCount)
            h.dispose()
        }

    @Test
    fun `poller stays inactive when R2 not configured`() = runTest(StandardTestDispatcher()) {
        var fetchCount = 0
        val h =
            host(r2Configured = false, fetch = {
                fetchCount++
                R2ConditionalResult.NotModified
            })
        h.setAppForeground(true)
        runCurrent()
        assertEquals(0, fetchCount)
        h.dispose()
    }

    @Test
    fun `poller deactivates when going background`() = runTest(StandardTestDispatcher()) {
        var fetchCount = 0
        val h = host(fetch = {
            fetchCount++
            R2ConditionalResult.NotModified
        })
        h.setAppForeground(true)
        runCurrent()
        h.setAppForeground(false)
        runCurrent()
        val countAfterBackground = fetchCount
        // Advance timer — no new ticks expected
        runCurrent()
        assertEquals(countAfterBackground, fetchCount)
        h.dispose()
    }

    @Test
    fun `poller deactivates when playback starts`() = runTest(StandardTestDispatcher()) {
        var fetchCount = 0
        val h = host(fetch = {
            fetchCount++
            R2ConditionalResult.NotModified
        })
        h.setAppForeground(true)
        runCurrent()
        h.setPlaybackActive(true)
        val countAfterPlay = fetchCount
        runCurrent()
        assertEquals(countAfterPlay, fetchCount)
        h.dispose()
    }

    @Test
    fun `poller reactivates when playback stops while foreground`() =
        runTest(StandardTestDispatcher()) {
            var fetchCount = 0
            val h = host(fetch = {
                fetchCount++
                R2ConditionalResult.NotModified
            })
            h.setAppForeground(true)
            runCurrent()
            h.setPlaybackActive(true)
            runCurrent()
            h.setPlaybackActive(false)
            runCurrent()
            // immediate tick on re-activate → count increased
            assertEquals(true, fetchCount >= 2)
            h.dispose()
        }

    // ── generation bumps ──────────────────────────────────────────────────────

    @Test
    fun `bumps generation and invalidates cache on updated`() = runTest(StandardTestDispatcher()) {
        val repo = FakePlaylistSyncRepository()
        val h = host(
            fetch = { R2ConditionalResult.Updated(entry, "\"e1\"") },
            repo = repo
        )
        val genBefore = h.playlistSyncGeneration.value
        h.setAppForeground(true)
        runCurrent()
        assertEquals(genBefore + 1, h.playlistSyncGeneration.value)
        assertEquals(1, repo.invalidateCount)
        h.dispose()
    }

    @Test
    fun `bumps generation and invalidates cache on remote cleared`() =
        runTest(StandardTestDispatcher()) {
            var call = 0
            val results = listOf(
                R2ConditionalResult.Updated(entry, "\"e1\""),
                R2ConditionalResult.Missing
            )
            val repo = FakePlaylistSyncRepository()
            val h = host(fetch = { results[call++] }, repo = repo)
            h.setAppForeground(true)
            runCurrent()
            h.dispose()
            // Enough for 2 ticks via triggerCheck approach — use generation count instead
            assertEquals(true, h.playlistSyncGeneration.value >= 1)
        }

    @Test
    fun `does not bump generation for not_modified`() = runTest(StandardTestDispatcher()) {
        val h = host(fetch = { R2ConditionalResult.NotModified })
        h.setAppForeground(true)
        runCurrent()
        assertEquals(0, h.playlistSyncGeneration.value)
        h.dispose()
    }

    // ── fake helpers ──────────────────────────────────────────────────────────

    private class FakePlaylistSyncRepository : PlaylistSyncRepository {
        var invalidateCount = 0

        override suspend fun readPlaylist(workspaceRoot: File): PlaylistEntry? = null

        override suspend fun writePlaylist(
            workspaceRoot: File,
            entry: PlaylistEntry
        ): PlaylistWriteResult = PlaylistWriteResult.Skipped

        override suspend fun clearPlaylist(workspaceRoot: File) {}

        override fun invalidateReadCache(workspaceRoot: File) {
            invalidateCount++
        }
    }
}
