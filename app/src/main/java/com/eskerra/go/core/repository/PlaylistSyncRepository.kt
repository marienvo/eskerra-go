package com.eskerra.go.core.repository

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import java.io.File

/**
 * R2-backed playlist sync, mirroring the orchestration in
 * `apps/mobile/src/core/storage/eskerraStorage.ts`. Reads/writes resolve R2
 * config from the shared vault settings and track per-device watermarks in
 * local settings. Concurrent reads per workspace are coalesced.
 */
interface PlaylistSyncRepository {
    /**
     * R2 unconfigured → `null` and watermarks cleared (no local file read).
     * Configured → signed GET, watermarks updated; on error → `null` + cleared.
     */
    suspend fun readPlaylist(workspaceRoot: File): PlaylistEntry?

    /**
     * No R2 → [PlaylistWriteResult.Skipped]; remote strictly newer than known →
     * [PlaylistWriteResult.Superseded]; otherwise PUT merged → [PlaylistWriteResult.Saved].
     */
    suspend fun writePlaylist(workspaceRoot: File, entry: PlaylistEntry): PlaylistWriteResult

    /** DELETE from R2 when configured, clear watermarks, unlink any legacy local file. */
    suspend fun clearPlaylist(workspaceRoot: File)

    /** Drop the coalesced read cache for a workspace (e.g. after an external sync). */
    fun invalidateReadCache(workspaceRoot: File)
}
