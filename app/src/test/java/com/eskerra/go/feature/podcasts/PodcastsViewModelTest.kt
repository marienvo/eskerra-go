package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
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

class PodcastsViewModelTest {

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
    fun init_showsContentWhenCatalogHasSections() = runTest {
        val episode = sampleEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section))
                    )
                )
            )
        )

        advanceUntilIdle()

        assertEquals(PodcastsUiState.Content(sections = listOf(section)), viewModel.uiState.value)
    }

    @Test
    fun init_showsEmptyWhenAllEpisodesArePlayed() = runTest {
        val emptyCatalog = PodcastCatalog(allEpisodes = emptyList(), sections = emptyList())
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(Result.success(emptyCatalog))
            )
        )

        advanceUntilIdle()

        assertEquals(PodcastsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun refresh_mapsWorkspaceMissingToError() = runTest {
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.failure(PodcastCatalogException(PodcastCatalogError.WorkspaceMissing))
                )
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PodcastsUiState.Error)
        assertEquals(
            PodcastsViewModel.WORKSPACE_MISSING_MESSAGE,
            (state as PodcastsUiState.Error).message
        )
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

    private class FakePodcastCatalogRepository(private val result: Result<PodcastCatalog>) :
        PodcastCatalogRepository {
        override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
            result
    }
}
