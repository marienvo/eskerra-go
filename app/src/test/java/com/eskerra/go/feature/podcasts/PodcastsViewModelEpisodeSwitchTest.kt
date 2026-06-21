package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PodcastsViewModelEpisodeSwitchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun persistence() = podcastsViewModelPersistenceDefaults()

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
    fun onEpisodeClick_whilePlayingDifferentEpisode_emitsHintAndDoesNotPlay() = runTest {
        val episode = samplePodcastEpisode()
        val otherEpisode = samplePodcastEpisodeTwo()
        val section = PodcastSection(
            episodes = listOf(episode, otherEpisode),
            rssFeedUrl = null,
            title = "News"
        )
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = viewModelWithEpisodes(episode, otherEpisode, section, playerDriver)
        advanceUntilIdle()

        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PLAYING,
                positionMs = 30_000L,
                durationMs = 60_000L
            )
        )
        advanceUntilIdle()

        val events = mutableListOf<PodcastsUiEvent>()
        val collectJob = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.onEpisodeClick(otherEpisode)
        advanceUntilIdle()

        assertEquals(listOf(PodcastsUiEvent.PauseToSwitchEpisode), events)
        assertNull(playerDriver.playedEpisode)
        collectJob.cancel()
    }

    @Test
    fun onEpisodeClick_whilePaused_allowsSwitchingToDifferentEpisode() = runTest {
        val episode = samplePodcastEpisode()
        val otherEpisode = samplePodcastEpisodeTwo()
        val section = PodcastSection(
            episodes = listOf(episode, otherEpisode),
            rssFeedUrl = null,
            title = "News"
        )
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = viewModelWithEpisodes(episode, otherEpisode, section, playerDriver)
        advanceUntilIdle()

        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.PAUSED,
                positionMs = 30_000L,
                durationMs = 60_000L
            )
        )
        advanceUntilIdle()

        viewModel.onEpisodeClick(otherEpisode)
        advanceUntilIdle()

        assertEquals(otherEpisode.id, playerDriver.playedEpisode?.id)
    }

    @Test
    fun onEpisodeClick_whileLoadingDifferentEpisode_emitsHintAndDoesNotPlay() = runTest {
        val episode = samplePodcastEpisode()
        val otherEpisode = samplePodcastEpisodeTwo()
        val section = PodcastSection(
            episodes = listOf(episode, otherEpisode),
            rssFeedUrl = null,
            title = "News"
        )
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = viewModelWithEpisodes(episode, otherEpisode, section, playerDriver)
        advanceUntilIdle()

        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.LOADING,
                positionMs = 0L,
                durationMs = 60_000L,
                transportBusy = true
            )
        )
        advanceUntilIdle()

        val events = mutableListOf<PodcastsUiEvent>()
        val collectJob = launch { viewModel.uiEvents.collect { events.add(it) } }

        viewModel.onEpisodeClick(otherEpisode)
        advanceUntilIdle()

        assertEquals(listOf(PodcastsUiEvent.PauseToSwitchEpisode), events)
        assertNull(playerDriver.playedEpisode)
        collectJob.cancel()
    }

    private fun viewModelWithEpisodes(
        episode: PodcastEpisode,
        otherEpisode: PodcastEpisode,
        section: PodcastSection,
        playerDriver: FakePodcastPlayerDriver
    ): PodcastsViewModel = PodcastsViewModel(
        config = config,
        filesDir = temp.newFolder("files"),
        loadPodcastCatalog = LoadPodcastCatalog(
            FakePodcastCatalogRepository(
                Result.success(
                    PodcastCatalog(
                        allEpisodes = listOf(episode, otherEpisode),
                        sections = listOf(section)
                    )
                )
            )
        ),
        markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
        podcastPlaylistSync = noopPodcastPlaylistSync(),
        podcastPlayerDriver = playerDriver,
        syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
        loadPodcastArtwork = noopLoadPodcastArtwork(),
        persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
        clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
        loadLocalSettings = persistence().loadLocalSettings
    )
}
