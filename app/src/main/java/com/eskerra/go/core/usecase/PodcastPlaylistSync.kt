package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistReadOutcome
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.playlist.buildPlaylistEntryForWrite
import com.eskerra.go.core.vault.R2Settings
import java.io.File

class PodcastPlaylistSync(
    private val readPlaylist: ReadPlaylist,
    private val writePlaylist: WritePlaylist,
    private val clearPlaylist: ClearPlaylist,
    private val loadVaultSettings: LoadVaultSettings,
    private val ensureDeviceInstanceId: EnsureDeviceInstanceId,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun isR2Configured(workspaceRoot: File): Boolean =
        loadVaultSettings(workspaceRoot).getOrNull()
            ?.let(R2Settings::isVaultR2PlaylistConfigured) == true

    suspend fun read(workspaceRoot: File): PlaylistEntry? = readPlaylist(workspaceRoot)

    suspend fun readOutcome(workspaceRoot: File): PlaylistReadOutcome =
        readPlaylist.outcome(workspaceRoot)

    suspend fun clear(workspaceRoot: File) {
        clearPlaylist(workspaceRoot)
    }

    suspend fun persist(
        workspaceRoot: File,
        episode: PodcastEpisode,
        positionMs: Long,
        durationMs: Long?,
        knownEntry: PlaylistEntry?
    ): PlaylistWriteResult {
        if (!isR2Configured(workspaceRoot)) return PlaylistWriteResult.Skipped
        val deviceId = ensureDeviceInstanceId()
        val base = knownEntry ?: PlaylistEntry(
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            positionMs = 0L,
            durationMs = durationMs
        )
        val entry = buildPlaylistEntryForWrite(
            base = base,
            deviceInstanceId = deviceId,
            nowMs = clock(),
            positionMs = positionMs,
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            durationMs = durationMs
        )
        return writePlaylist(workspaceRoot, entry)
    }
}
