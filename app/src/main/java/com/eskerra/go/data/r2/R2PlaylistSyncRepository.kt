package com.eskerra.go.data.r2

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.playlist.isRemotePlaylistNewerThanKnown
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.PlaylistSyncRepository
import com.eskerra.go.core.repository.R2PlaylistClient
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.core.vault.VaultLayout
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * R2-backed [PlaylistSyncRepository], mirroring the orchestration in
 * `apps/mobile/src/core/storage/eskerraStorage.ts` (`readPlaylist`,
 * `writePlaylist`, `clearPlaylist`, `readPlaylistCoalesced`).
 *
 * Shared R2 config comes from [settingsRepository]; per-device watermarks
 * (`playlistKnownUpdatedAtMs` / `playlistKnownControlRevision`) and the device
 * id live in [localSettingsStore]. Blocking R2 HTTP runs on [ioDispatcher].
 */
class R2PlaylistSyncRepository(
    private val settingsRepository: VaultSettingsRepository,
    private val localSettingsStore: LocalSettingsStore,
    private val r2Client: R2PlaylistClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newDeviceId: () -> String = { UUID.randomUUID().toString() }
) : PlaylistSyncRepository {

    /** Keeps the settled read per workspace so a prime can be reused (see TS coalescer). */
    private val readCache = mutableMapOf<String, Deferred<PlaylistEntry?>>()

    override suspend fun readPlaylist(workspaceRoot: File): PlaylistEntry? {
        val key = workspaceRoot.path
        val existing: Deferred<PlaylistEntry?>?
        val owned = CompletableDeferred<PlaylistEntry?>()
        synchronized(readCache) {
            existing = readCache[key]
            if (existing == null) readCache[key] = owned
        }
        existing?.let { return it.await() }

        return try {
            val result = doRead(workspaceRoot)
            owned.complete(result)
            result
        } catch (e: CancellationException) {
            dropCache(key, owned)
            if (!owned.isCompleted) {
                owned.completeExceptionally(IOException("Playlist read was cancelled", e))
            }
            throw e
        } catch (e: Throwable) {
            dropCache(key, owned)
            owned.completeExceptionally(e)
            throw e
        }
    }

    private suspend fun doRead(workspaceRoot: File): PlaylistEntry? {
        val r2 = resolveR2(workspaceRoot)
        if (r2 == null) {
            persistKnown(null, null)
            return null
        }
        return try {
            val remote = withContext(ioDispatcher) { r2Client.get(r2) }
            persistKnown(remote?.updatedAt, remote?.controlRevision)
            remote
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            persistKnown(null, null)
            null
        }
    }

    override suspend fun writePlaylist(
        workspaceRoot: File,
        entry: PlaylistEntry
    ): PlaylistWriteResult {
        var local = localSettingsStore.load()
        if (local.deviceInstanceId.isEmpty()) {
            local = local.copy(deviceInstanceId = newDeviceId())
            localSettingsStore.save(local)
        }
        val knownUpdated = local.playlistKnownUpdatedAtMs ?: 0L
        val knownRev = local.playlistKnownControlRevision ?: 0L

        val r2 = resolveR2(workspaceRoot) ?: return PlaylistWriteResult.Skipped
        val key = workspaceRoot.path

        val remote = withContext(ioDispatcher) { r2Client.get(r2) }
        if (remote != null && isRemotePlaylistNewerThanKnown(remote, knownUpdated, knownRev)) {
            persistKnown(remote.updatedAt, remote.controlRevision)
            cacheResolved(key, remote)
            return PlaylistWriteResult.Superseded(remote)
        }

        val nextTs = maxOf(clock(), remote?.updatedAt ?: 0L, knownUpdated, entry.updatedAt)
        val saved = entry.copy(updatedAt = nextTs)
        withContext(ioDispatcher) { r2Client.put(r2, saved) }
        persistKnown(saved.updatedAt, saved.controlRevision)
        cacheResolved(key, saved)
        return PlaylistWriteResult.Saved(saved)
    }

    override suspend fun clearPlaylist(workspaceRoot: File) {
        val r2 = resolveR2(workspaceRoot)
        if (r2 != null) {
            withContext(ioDispatcher) { r2Client.delete(r2) }
        }
        persistKnown(null, null)

        val legacy = File(workspaceRoot, VaultLayout.PLAYLIST_PATH)
        if (legacy.isFile) {
            withContext(ioDispatcher) { legacy.delete() }
        }
        cacheResolved(workspaceRoot.path, null)
    }

    override fun invalidateReadCache(workspaceRoot: File) {
        synchronized(readCache) { readCache.remove(workspaceRoot.path) }
    }

    /** R2Config only when all four fields are configured, else `null`. */
    private suspend fun resolveR2(workspaceRoot: File): R2Config? {
        val settings: EskerraSettings = settingsRepository.loadShared(workspaceRoot).getOrThrow()
        return settings.r2?.takeIf { R2Settings.isVaultR2PlaylistConfigured(settings) }
    }

    /** Re-read local, write only when the watermarks actually change (preserve other fields). */
    private suspend fun persistKnown(nextUpdatedAtMs: Long?, nextControlRevision: Long?) {
        val local = localSettingsStore.load()
        if (local.playlistKnownUpdatedAtMs == nextUpdatedAtMs &&
            local.playlistKnownControlRevision == nextControlRevision
        ) {
            return
        }
        localSettingsStore.save(
            local.copy(
                playlistKnownUpdatedAtMs = nextUpdatedAtMs,
                playlistKnownControlRevision = nextControlRevision
            )
        )
    }

    private fun cacheResolved(key: String, value: PlaylistEntry?) {
        synchronized(readCache) { readCache[key] = CompletableDeferred(value) }
    }

    private fun dropCache(key: String, owned: Deferred<PlaylistEntry?>) {
        synchronized(readCache) { if (readCache[key] === owned) readCache.remove(key) }
    }
}
