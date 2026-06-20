package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PodcastsViewModelSelectionTest {

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
    fun onEpisodeArtworkClick_togglesSelectionAndNotifiesMiniPlayerExit() = runTest {
        val episode = samplePodcastEpisode()
        val other = episode.copy(id = "other", title = "Other")
        val section = PodcastSection(
            episodes = listOf(episode, other),
            rssFeedUrl = null,
            title = "News"
        )
        var miniPlayerExitCount = 0
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(
                            allEpisodes = listOf(episode, other),
                            sections = listOf(section)
                        )
                    )
                )
            ),
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            onExitMiniPlayerArtworkMode = { miniPlayerExitCount++ }
        )
        advanceUntilIdle()

        viewModel.onEpisodeArtworkClick(episode)
        var content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(setOf(episode.id), content.selectedEpisodeIds)
        assertEquals(1, miniPlayerExitCount)

        viewModel.onEpisodeArtworkClick(episode)
        content = viewModel.uiState.value as PodcastsUiState.Content
        assertTrue(content.selectedEpisodeIds.isEmpty())
    }

    @Test
    fun markSelectedEpisodes_archivesSelectionAndReloadsCatalog() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val catalogRepository = SwitchingPodcastCatalogRepository(
            first = PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section)),
            second = PodcastCatalog(allEpisodes = emptyList(), sections = emptyList())
        )
        val fileRepository = InMemoryPodcastFileRepository(
            mutableMapOf(
                "General/2026 News - podcasts.md" to
                    "- [ ] 2026-03-15; Episode title [\u25B6](https://cdn/episode.mp3) (Daily)\n"
            )
        )
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(catalogRepository),
            markPodcastEpisodesPlayed = MarkPodcastEpisodesPlayed(
                podcastFileRepository = fileRepository,
                syncPodcastChange = { _, _ ->
                    Result.success(
                        PodcastSyncResult(
                            committed = true,
                            commitId = "abc",
                            pushed = true,
                            pendingPush = false
                        )
                    )
                }
            ),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork()
        )
        advanceUntilIdle()
        viewModel.onEpisodeArtworkClick(episode)

        viewModel.markSelectedEpisodes()
        advanceUntilIdle()

        assertEquals(PodcastsUiState.Empty, viewModel.uiState.value)
    }
}
