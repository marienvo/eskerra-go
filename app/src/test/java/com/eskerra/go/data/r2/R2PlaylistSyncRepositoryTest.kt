package com.eskerra.go.data.r2

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.R2PlaylistClient
import com.eskerra.go.core.repository.VaultSettingsRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class R2PlaylistSyncRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val r2Config = R2Config(
        endpoint = "https://acc.r2.cloudflarestorage.com",
        bucket = "bucket",
        accessKeyId = "key",
        secretAccessKey = "secret"
    )

    private fun entry(updatedAt: Long = 0L, controlRevision: Long = 0L, owner: String = "") =
        PlaylistEntry(
            episodeId = "ep",
            mp3Url = "https://x/ep.mp3",
            positionMs = 100,
            durationMs = 1000,
            updatedAt = updatedAt,
            playbackOwnerId = owner,
            controlRevision = controlRevision
        )

    // ── readPlaylist ───────────────────────────────────────────────────────────

    @Test
    fun `readPlaylist without R2 returns null and clears watermarks without touching client`() =
        runTest {
            val client = FakeR2Client()
            val local = FakeLocalSettingsStore(
                EskerraLocalSettings(
                    playlistKnownUpdatedAtMs = 5,
                    playlistKnownControlRevision = 2
                )
            )
            val repo = repo(EskerraSettings(r2 = null), local, client)

            assertNull(repo.readPlaylist(tempFolder.root))

            assertEquals(0, client.getCount)
            assertNull(local.current.playlistKnownUpdatedAtMs)
            assertNull(local.current.playlistKnownControlRevision)
        }

    @Test
    fun `readPlaylist with R2 returns remote and updates watermarks`() = runTest {
        val remote = entry(updatedAt = 42, controlRevision = 3)
        val client = FakeR2Client(getResult = remote)
        val local = FakeLocalSettingsStore()
        val repo = repo(EskerraSettings(r2 = r2Config), local, client)

        assertEquals(remote, repo.readPlaylist(tempFolder.root))

        assertEquals(1, client.getCount)
        assertEquals(42L, local.current.playlistKnownUpdatedAtMs)
        assertEquals(3L, local.current.playlistKnownControlRevision)
    }

    @Test
    fun `readPlaylist clears watermarks when the GET fails`() = runTest {
        val client = FakeR2Client(getThrows = R2PlaylistException("boom"))
        val local = FakeLocalSettingsStore(
            EskerraLocalSettings(playlistKnownUpdatedAtMs = 9, playlistKnownControlRevision = 1)
        )
        val repo = repo(EskerraSettings(r2 = r2Config), local, client)

        assertNull(repo.readPlaylist(tempFolder.root))

        assertNull(local.current.playlistKnownUpdatedAtMs)
        assertNull(local.current.playlistKnownControlRevision)
    }

    @Test
    fun `readPlaylist coalesces and reuses the settled result`() = runTest {
        val client = FakeR2Client(getResult = entry(updatedAt = 1))
        val repo = repo(EskerraSettings(r2 = r2Config), FakeLocalSettingsStore(), client)

        val first = repo.readPlaylist(tempFolder.root)
        val second = repo.readPlaylist(tempFolder.root)

        assertEquals(first, second)
        assertEquals(1, client.getCount)
    }

    @Test
    fun `readPlaylist coalesced waiters receive failure not cancellation when owner cancelled`() =
        runTest {
            val hangingSettings = object : VaultSettingsRepository {
                override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> {
                    awaitCancellation()
                }

                override suspend fun saveShared(
                    workspaceRoot: File,
                    settings: EskerraSettings
                ): Result<Unit> = Result.success(Unit)
            }
            val client = FakeR2Client(getResult = entry(updatedAt = 1))
            val repo = R2PlaylistSyncRepository(
                settingsRepository = hangingSettings,
                localSettingsStore = FakeLocalSettingsStore(),
                r2Client = client,
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )

            var waiterError: Throwable? = null
            val owner = launch { repo.readPlaylist(tempFolder.root) }
            yield()
            val waiter = launch {
                try {
                    repo.readPlaylist(tempFolder.root)
                } catch (e: Throwable) {
                    waiterError = e
                }
            }
            yield()
            owner.cancel()
            owner.join()
            waiter.join()

            assertNotNull(waiterError)
            assertTrue(waiterError is IOException)
            assertFalse(waiterError is CancellationException)

            repo.invalidateReadCache(tempFolder.root)
            val fastRepo = repo(
                EskerraSettings(r2 = r2Config),
                FakeLocalSettingsStore(),
                client
            )
            assertEquals(entry(updatedAt = 1), fastRepo.readPlaylist(tempFolder.root))
        }

    @Test
    fun `invalidateReadCache forces a fresh read`() = runTest {
        val client = FakeR2Client(getResult = entry(updatedAt = 1))
        val repo = repo(EskerraSettings(r2 = r2Config), FakeLocalSettingsStore(), client)

        repo.readPlaylist(tempFolder.root)
        repo.invalidateReadCache(tempFolder.root)
        repo.readPlaylist(tempFolder.root)

        assertEquals(2, client.getCount)
    }

    // ── writePlaylist ──────────────────────────────────────────────────────────

    @Test
    fun `writePlaylist without R2 is skipped and never PUTs`() = runTest {
        val client = FakeR2Client()
        val repo = repo(EskerraSettings(r2 = null), FakeLocalSettingsStore(), client)

        val result = repo.writePlaylist(tempFolder.root, entry())

        assertEquals(PlaylistWriteResult.Skipped, result)
        assertEquals(0, client.putCount)
    }

    @Test
    fun `writePlaylist saves with advanced updatedAt and updates watermarks`() = runTest {
        val client = FakeR2Client(getResult = entry(updatedAt = 500, controlRevision = 0))
        val local = FakeLocalSettingsStore(
            EskerraLocalSettings(
                deviceInstanceId = "dev",
                playlistKnownUpdatedAtMs = 500,
                playlistKnownControlRevision = 0
            )
        )
        val repo = repo(
            EskerraSettings(r2 = r2Config),
            local,
            client,
            clock = { 100L }
        )

        val result = repo.writePlaylist(tempFolder.root, entry(updatedAt = 50, controlRevision = 0))

        result as PlaylistWriteResult.Saved
        // max(now=100, remote=500, known=500, entry=50)
        assertEquals(500L, result.entry.updatedAt)
        assertEquals(1, client.putCount)
        assertEquals(500L, client.putEntry?.updatedAt)
        assertEquals(500L, local.current.playlistKnownUpdatedAtMs)
        assertEquals(0L, local.current.playlistKnownControlRevision)
    }

    @Test
    fun `writePlaylist is superseded when remote is newer than known`() = runTest {
        val remote = entry(updatedAt = 10, controlRevision = 5)
        val client = FakeR2Client(getResult = remote)
        val local = FakeLocalSettingsStore(
            EskerraLocalSettings(
                deviceInstanceId = "dev",
                playlistKnownUpdatedAtMs = 0,
                playlistKnownControlRevision = 1
            )
        )
        val repo = repo(EskerraSettings(r2 = r2Config), local, client)

        val result = repo.writePlaylist(tempFolder.root, entry(controlRevision = 2))

        result as PlaylistWriteResult.Superseded
        assertEquals(remote, result.entry)
        assertEquals(0, client.putCount)
        assertEquals(10L, local.current.playlistKnownUpdatedAtMs)
        assertEquals(5L, local.current.playlistKnownControlRevision)
    }

    @Test
    fun `writePlaylist ensures a device id when missing`() = runTest {
        val client = FakeR2Client(getResult = null)
        val local = FakeLocalSettingsStore()
        val repo = repo(
            EskerraSettings(r2 = r2Config),
            local,
            client,
            newDeviceId = { "generated-id" }
        )

        repo.writePlaylist(tempFolder.root, entry())

        assertEquals("generated-id", local.current.deviceInstanceId)
    }

    // ── clearPlaylist ──────────────────────────────────────────────────────────

    @Test
    fun `clearPlaylist deletes from R2, clears watermarks and removes legacy file`() = runTest {
        val client = FakeR2Client()
        val local = FakeLocalSettingsStore(
            EskerraLocalSettings(playlistKnownUpdatedAtMs = 7, playlistKnownControlRevision = 3)
        )
        val legacy = legacyPlaylistFile()
        val repo = repo(EskerraSettings(r2 = r2Config), local, client)

        repo.clearPlaylist(tempFolder.root)

        assertEquals(1, client.deleteCount)
        assertNull(local.current.playlistKnownUpdatedAtMs)
        assertNull(local.current.playlistKnownControlRevision)
        assertFalse(legacy.exists())
    }

    @Test
    fun `clearPlaylist without R2 still clears watermarks and removes legacy file`() = runTest {
        val client = FakeR2Client()
        val local = FakeLocalSettingsStore(
            EskerraLocalSettings(playlistKnownUpdatedAtMs = 7, playlistKnownControlRevision = 3)
        )
        val legacy = legacyPlaylistFile()
        val repo = repo(EskerraSettings(r2 = null), local, client)

        repo.clearPlaylist(tempFolder.root)

        assertEquals(0, client.deleteCount)
        assertNull(local.current.playlistKnownUpdatedAtMs)
        assertFalse(legacy.exists())
    }

    @Test
    fun `clearPlaylist caches null so a follow-up read skips the client`() = runTest {
        val client = FakeR2Client(getResult = entry(updatedAt = 1))
        val repo = repo(EskerraSettings(r2 = r2Config), FakeLocalSettingsStore(), client)

        repo.clearPlaylist(tempFolder.root)
        assertNull(repo.readPlaylist(tempFolder.root))

        assertEquals(0, client.getCount)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private fun TestScope.repo(
        settings: EskerraSettings,
        local: FakeLocalSettingsStore,
        client: FakeR2Client,
        clock: () -> Long = { 0L },
        newDeviceId: () -> String = { "device" }
    ) = R2PlaylistSyncRepository(
        settingsRepository = FakeVaultSettingsRepository(settings),
        localSettingsStore = local,
        r2Client = client,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        clock = clock,
        newDeviceId = newDeviceId
    )

    private fun legacyPlaylistFile(): File {
        val file = File(tempFolder.root, ".eskerra/playlist.json")
        file.parentFile?.mkdirs()
        file.writeText("{}")
        assertTrue(file.exists())
        return file
    }

    private class FakeR2Client(
        private val getResult: PlaylistEntry? = null,
        private val getThrows: Throwable? = null
    ) : R2PlaylistClient {
        var getCount = 0
        var putCount = 0
        var deleteCount = 0
        var putEntry: PlaylistEntry? = null

        override fun get(config: R2Config): PlaylistEntry? {
            getCount++
            getThrows?.let { throw it }
            return getResult
        }

        override fun put(config: R2Config, entry: PlaylistEntry) {
            putCount++
            putEntry = entry
        }

        override fun delete(config: R2Config) {
            deleteCount++
        }
    }

    private class FakeLocalSettingsStore(
        var current: EskerraLocalSettings = EskerraLocalSettings()
    ) : LocalSettingsStore {
        override suspend fun load(): EskerraLocalSettings = current
        override suspend fun save(settings: EskerraLocalSettings) {
            current = settings
        }
    }

    private class FakeVaultSettingsRepository(private val settings: EskerraSettings) :
        VaultSettingsRepository {
        override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> =
            Result.success(settings)

        override suspend fun saveShared(
            workspaceRoot: File,
            settings: EskerraSettings
        ): Result<Unit> = Result.success(Unit)
    }
}
