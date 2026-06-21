package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastNativeSessionSnapshot
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.PlaylistSyncRepository
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestorePodcastPlaybackTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun livePlayingNativeSessionWinsOverStalePrimedSnapshot() = runTest {
        val episode = sampleEpisode()
        val driver = RestorePodcastPlayerDriver(
            initialState = PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PRIMED,
                positionMs = 12_000L,
                durationMs = 60_000L
            ),
            nativeSession = PodcastNativeSessionSnapshot(
                episodeId = episode.id,
                positionMs = 42_000L,
                durationMs = 60_000L,
                isPlaying = true
            )
        )
        val restore = RestorePodcastPlayback(
            loadPodcastCatalog = LoadPodcastCatalog(
                StaticPodcastCatalogRepository(
                    PodcastCatalog(allEpisodes = listOf(episode), sections = emptyList())
                )
            ),
            podcastPlaylistSync = restorePodcastPlaylistSync(),
            localSettingsStore = MutableLocalSettingsStore(
                EskerraLocalSettings(
                    lastShellMode = AppShellMode.HOME,
                    podcastEpisodeId = episode.id,
                    podcastMp3Url = episode.mp3Url,
                    podcastPositionMs = 12_000L,
                    podcastDurationMs = 60_000L,
                    podcastSnapshotUpdatedAtMs = 1_700_000_000_000L
                )
            ),
            podcastPlayerDriver = driver
        )

        val result = restore(
            config = config,
            filesDir = temp.newFolder("files"),
            workspaceRoot = null
        )

        assertTrue(result.hydrated)
        assertEquals(AppShellMode.PODCASTS, result.preferredShellMode)
        assertEquals(PodcastPlaybackPhase.PLAYING, driver.state.value.phase)
        assertEquals(42_000L, driver.state.value.positionMs)
        assertEquals(episode.id, driver.state.value.activeEpisode?.id)
    }

    private fun restorePodcastPlaylistSync(): PodcastPlaylistSync {
        val repo = object : PlaylistSyncRepository {
            override suspend fun readPlaylist(workspaceRoot: File): PlaylistEntry? = null
            override suspend fun writePlaylist(
                workspaceRoot: File,
                entry: PlaylistEntry
            ): PlaylistWriteResult = PlaylistWriteResult.Skipped

            override suspend fun clearPlaylist(workspaceRoot: File) = Unit
            override fun invalidateReadCache(workspaceRoot: File) = Unit
        }
        val vaultRepo = object : VaultSettingsRepository {
            override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> =
                Result.success(EskerraSettings())

            override suspend fun saveShared(
                workspaceRoot: File,
                settings: EskerraSettings
            ): Result<Unit> = Result.success(Unit)
        }
        val localStore = MutableLocalSettingsStore(EskerraLocalSettings(deviceInstanceId = "test"))
        return PodcastPlaylistSync(
            readPlaylist = ReadPlaylist(repo),
            writePlaylist = WritePlaylist(repo),
            clearPlaylist = ClearPlaylist(repo),
            loadVaultSettings = LoadVaultSettings(vaultRepo),
            ensureDeviceInstanceId = EnsureDeviceInstanceId(localStore)
        )
    }

    private class StaticPodcastCatalogRepository(private val catalog: PodcastCatalog) :
        PodcastCatalogRepository {
        override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
            Result.success(catalog)
    }

    private class MutableLocalSettingsStore(private var settings: EskerraLocalSettings) :
        LocalSettingsStore {
        override suspend fun load(): EskerraLocalSettings = settings
        override suspend fun save(settings: EskerraLocalSettings) {
            this.settings = settings
        }
    }

    private class RestorePodcastPlayerDriver(
        initialState: PodcastPlaybackState,
        private val nativeSession: PodcastNativeSessionSnapshot?
    ) : PodcastPlayerDriver {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<PodcastPlaybackState> = mutableState

        override fun play(episode: PodcastEpisode, startPositionMs: Long) {
            mutableState.value = PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.LOADING,
                positionMs = startPositionMs
            )
        }

        override fun hydrate(episode: PodcastEpisode, positionMs: Long, durationMs: Long?) {
            mutableState.value = PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PRIMED,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }

        override fun pause() {
            mutableState.value = mutableState.value.copy(phase = PodcastPlaybackPhase.PAUSED)
        }

        override fun resume() {
            mutableState.value = mutableState.value.copy(phase = PodcastPlaybackPhase.PLAYING)
        }

        override fun stop() {
            mutableState.value = PodcastPlaybackState()
        }

        override fun seekBy(deltaMs: Long) = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun currentNativeSession(): PodcastNativeSessionSnapshot? = nativeSession
        override fun release() = Unit
    }

    private fun sampleEpisode() = PodcastEpisode(
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
}
