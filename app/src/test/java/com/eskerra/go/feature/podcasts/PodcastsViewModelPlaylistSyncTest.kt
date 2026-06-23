package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.PlaylistReadOutcome
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastsViewModelPlaylistSyncTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun playlistGenerationChange_afterEarlyUserPause_keepsPausedPlayerVisible() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, _) = recordingPodcastPlaylistSyncForTest()
        val persistence = podcastsViewModelPersistenceDefaults()
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = filesDir,
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section))
                    )
                )
            ),
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = playlistSync,
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence.persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence.clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence.loadLocalSettings
        )
        advanceUntilIdle()
        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PLAYING,
                positionMs = 3_000L,
                durationMs = 60_000L
            )
        )
        runCurrent()

        viewModel.pausePlayback()
        runCurrent()
        viewModel.onPlaylistSyncGenerationChanged(1)
        runCurrent()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.PAUSED, content.playerState.phase)
        assertTrue(content.playerState.hasActiveEpisode)
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
    }

    @Test
    fun playlistGenerationChange_afterLaterUserPause_keepsPausedPlayerVisible() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, playlistRepo) = recordingPodcastPlaylistSyncForTest()
        val persistence = podcastsViewModelPersistenceDefaults()
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = filesDir,
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section))
                    )
                )
            ),
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = playlistSync,
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence.persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence.clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence.loadLocalSettings
        )
        advanceUntilIdle()
        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PLAYING,
                positionMs = 30_000L,
                durationMs = 60_000L
            )
        )
        runCurrent()

        viewModel.pausePlayback()
        advanceTimeBy(600L)
        advanceUntilIdle()
        assertEquals(episode.id, playlistRepo.entry?.episodeId)
        viewModel.onPlaylistSyncGenerationChanged(1)
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.PAUSED, content.playerState.phase)
        assertTrue(content.playerState.hasActiveEpisode)
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
    }

    @Test
    fun emptyRemote_withMatchingLocalSnapshot_keepsRestoredSessionAndRepublishesToR2() = runTest {
        val episode = samplePodcastEpisode()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, playlistRepo) = recordingPodcastPlaylistSyncForTest()
        val persistence = podcastsViewModelPersistenceDefaults(snapshotStoreFor(episode))
        val viewModel = buildViewModel(episode, playerDriver, playlistSync, persistence)
        advanceUntilIdle()

        // A cold-launch restore surfaces the session as a PRIMED resume hint.
        playerDriver.hydrate(episode = episode, positionMs = 30_000L, durationMs = 60_000L)
        runCurrent()

        viewModel.onPlaylistSyncGenerationChanged(1)
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.PRIMED, content.playerState.phase)
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
        assertEquals(episode.id, playlistRepo.entry?.episodeId)
        assertEquals(0, playlistRepo.clearCalls)
    }

    @Test
    fun emptyRemote_withoutLocalSnapshot_tearsDownDisposableHint() = runTest {
        val episode = samplePodcastEpisode()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, playlistRepo) = recordingPodcastPlaylistSyncForTest()
        val persistence = podcastsViewModelPersistenceDefaults()
        val viewModel = buildViewModel(episode, playerDriver, playlistSync, persistence)
        advanceUntilIdle()

        playerDriver.hydrate(episode = episode, positionMs = 30_000L, durationMs = 60_000L)
        runCurrent()

        viewModel.onPlaylistSyncGenerationChanged(1)
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.STOPPED, content.playerState.phase)
        assertNull(playlistRepo.entry)
    }

    @Test
    fun unavailableRemote_neverTearsDownRestoredSession() = runTest {
        val episode = samplePodcastEpisode()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, playlistRepo) = recordingPodcastPlaylistSyncForTest(
            forcedReadOutcome = PlaylistReadOutcome.Unavailable
        )
        val persistence = podcastsViewModelPersistenceDefaults(snapshotStoreFor(episode))
        val viewModel = buildViewModel(episode, playerDriver, playlistSync, persistence)
        advanceUntilIdle()

        playerDriver.hydrate(episode = episode, positionMs = 30_000L, durationMs = 60_000L)
        runCurrent()

        viewModel.onPlaylistSyncGenerationChanged(1)
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.PRIMED, content.playerState.phase)
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
        assertNull(playlistRepo.entry)
        assertEquals(0, playlistRepo.clearCalls)
    }

    @Test
    fun emptyRemote_withPriorKnownWrite_tearsDownSessionAsDeliberateClear() = runTest {
        // hadPriorKnownWrite=true means the watermark was set before this read, so another device
        // cleared the playlist deliberately. This device must not re-publish its local session.
        val episode = samplePodcastEpisode()
        val playerDriver = FakePodcastPlayerDriver()
        val (playlistSync, playlistRepo) = recordingPodcastPlaylistSyncForTest(
            forcedReadOutcome = PlaylistReadOutcome.Empty(hadPriorKnownWrite = true)
        )
        val persistence = podcastsViewModelPersistenceDefaults(snapshotStoreFor(episode))
        val viewModel = buildViewModel(episode, playerDriver, playlistSync, persistence)
        advanceUntilIdle()

        playerDriver.hydrate(episode = episode, positionMs = 30_000L, durationMs = 60_000L)
        runCurrent()

        viewModel.onPlaylistSyncGenerationChanged(1)
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.STOPPED, content.playerState.phase)
        assertNull(playlistRepo.entry)
        assertEquals(0, playlistRepo.clearCalls)
    }

    private fun snapshotStoreFor(
        episode: com.eskerra.go.core.model.PodcastEpisode
    ): RecordingLocalSettingsStore = RecordingLocalSettingsStore().apply {
        settings = EskerraLocalSettings(
            deviceInstanceId = "device-1",
            podcastEpisodeId = episode.id,
            podcastMp3Url = episode.mp3Url,
            podcastPositionMs = 30_000L,
            podcastDurationMs = 60_000L,
            podcastSnapshotUpdatedAtMs = 1_000L
        )
    }

    private fun buildViewModel(
        episode: com.eskerra.go.core.model.PodcastEpisode,
        playerDriver: FakePodcastPlayerDriver,
        playlistSync: com.eskerra.go.core.usecase.PodcastPlaylistSync,
        persistence: PodcastsViewModelPersistence
    ): PodcastsViewModel {
        val filesDir = temp.newFolder()
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        return PodcastsViewModel(
            config = config,
            filesDir = filesDir,
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section))
                    )
                )
            ),
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = playlistSync,
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence.persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence.clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence.loadLocalSettings
        )
    }
}
