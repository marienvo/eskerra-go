package com.eskerra.go.feature.podcasts

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
}
