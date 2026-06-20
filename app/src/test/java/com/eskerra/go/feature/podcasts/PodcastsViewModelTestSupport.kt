package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.PlaylistSyncRepository
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.core.repository.PodcastFileRepository
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.usecase.ClearPlaylist
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.ReadPlaylist
import com.eskerra.go.core.usecase.WritePlaylist
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun samplePodcastEpisode() = PodcastEpisode(
    articleUrl = null,
    date = "2026-03-15",
    id = "https://cdn/episode.mp3",
    isListened = false,
    mp3Url = "https://cdn/episode.mp3",
    rssFeedUrl = null,
    sectionTitle = "News",
    seriesName = "Daily News",
    sourceFile = "2026 News - podcasts.md",
    title = "Episode title"
)

internal fun noopMarkPodcastEpisodesPlayed() = MarkPodcastEpisodesPlayed(
    podcastFileRepository = InMemoryPodcastFileRepository(mutableMapOf()),
    syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) }
)

internal fun noopPodcastPlaylistSync() = podcastPlaylistSyncForTest()

internal fun podcastPlaylistSyncForTest(
    readEntry: PlaylistEntry? = null,
    r2Configured: Boolean = false
): PodcastPlaylistSync {
    val repo = object : PlaylistSyncRepository {
        override suspend fun readPlaylist(workspaceRoot: File) = readEntry
        override suspend fun writePlaylist(workspaceRoot: File, entry: PlaylistEntry) =
            PlaylistWriteResult.Skipped
        override suspend fun clearPlaylist(workspaceRoot: File) = Unit
        override fun invalidateReadCache(workspaceRoot: File) = Unit
    }
    val vaultRepo = object : VaultSettingsRepository {
        override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> =
            Result.success(
                if (r2Configured) {
                    EskerraSettings(
                        r2 = R2Config(
                            endpoint = "https://example.r2.cloudflarestorage.com",
                            bucket = "bucket",
                            accessKeyId = "key",
                            secretAccessKey = "secret"
                        )
                    )
                } else {
                    EskerraSettings()
                }
            )

        override suspend fun saveShared(workspaceRoot: File, settings: EskerraSettings) =
            Result.success(Unit)
    }
    val localStore = object : LocalSettingsStore {
        override suspend fun load() = EskerraLocalSettings(deviceInstanceId = "device-1")
        override suspend fun save(settings: EskerraLocalSettings) = Unit
    }
    return PodcastPlaylistSync(
        readPlaylist = ReadPlaylist(repo),
        writePlaylist = WritePlaylist(repo),
        clearPlaylist = ClearPlaylist(repo),
        loadVaultSettings = LoadVaultSettings(vaultRepo),
        ensureDeviceInstanceId = EnsureDeviceInstanceId(localStore)
    )
}

internal class FakePodcastCatalogRepository(private val result: Result<PodcastCatalog>) :
    PodcastCatalogRepository {
    override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
        result
}

internal class SwitchingPodcastCatalogRepository(
    private val first: PodcastCatalog,
    private val second: PodcastCatalog
) : PodcastCatalogRepository {
    private var calls = 0
    override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> {
        calls += 1
        return Result.success(if (calls <= 1) first else second)
    }
}

internal class InMemoryPodcastFileRepository(val files: MutableMap<String, String>) :
    PodcastFileRepository {
    override suspend fun read(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String
    ): Result<String?> = Result.success(files[relativePath])

    override suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String,
        content: String
    ): Result<Unit> {
        files[relativePath] = content
        return Result.success(Unit)
    }
}

internal class FakePodcastPlayerDriver : PodcastPlayerDriver {
    private val mutableState = MutableStateFlow(PodcastPlaybackState())
    override val state: StateFlow<PodcastPlaybackState> = mutableState
    var playedEpisode: PodcastEpisode? = null
        private set

    override fun play(episode: PodcastEpisode, startPositionMs: Long) {
        playedEpisode = episode
        emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.LOADING,
                positionMs = startPositionMs,
                transportBusy = true
            )
        )
    }

    override fun hydrate(episode: PodcastEpisode, positionMs: Long, durationMs: Long?) {
        emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PRIMED,
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }

    override fun pause() {
        mutableState.value = mutableState.value.copy(phase = PodcastPlaybackPhase.PAUSED)
    }

    override fun resume() {
        mutableState.value = mutableState.value.copy(phase = PodcastPlaybackPhase.PLAYING)
    }

    override fun stop() {
        mutableState.value = mutableState.value.copy(phase = PodcastPlaybackPhase.STOPPED)
    }

    override fun seekBy(deltaMs: Long) {
        mutableState.value = mutableState.value.copy(
            positionMs = (mutableState.value.positionMs + deltaMs).coerceAtLeast(0L)
        )
    }

    override fun release() = Unit

    fun emit(state: PodcastPlaybackState) {
        mutableState.value = state
    }
}
