package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.core.repository.PodcastFileRepository
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            ),
            markPodcastEpisodesPlayed = noopMark(),
            podcastPlayerDriver = FakePodcastPlayerDriver()
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
            ),
            markPodcastEpisodesPlayed = noopMark(),
            podcastPlayerDriver = FakePodcastPlayerDriver()
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
            ),
            markPodcastEpisodesPlayed = noopMark(),
            podcastPlayerDriver = FakePodcastPlayerDriver()
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PodcastsUiState.Error)
        assertEquals(
            PodcastsViewModel.WORKSPACE_MISSING_MESSAGE,
            (state as PodcastsUiState.Error).message
        )
    }

    @Test
    fun markEpisodesPlayed_reloadsCatalogWhenUpdated() = runTest {
        val episode = sampleEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val catalogRepository = SwitchingCatalogRepository(
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
            podcastPlayerDriver = FakePodcastPlayerDriver()
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PodcastsUiState.Content)

        viewModel.markEpisodesPlayed(listOf(episode))
        advanceUntilIdle()

        assertEquals(PodcastsUiState.Empty, viewModel.uiState.value)
        assertTrue(
            fileRepository.files["General/2026 News - podcasts.md"]
                ?.contains("- [x] 2026-03-15") == true
        )
    }

    @Test
    fun markEpisodesPlayed_ignoresEmptySelection() = runTest {
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
            ),
            markPodcastEpisodesPlayed = noopMark(),
            podcastPlayerDriver = FakePodcastPlayerDriver()
        )
        advanceUntilIdle()

        viewModel.markEpisodesPlayed(emptyList())
        advanceUntilIdle()

        assertEquals(PodcastsUiState.Content(sections = listOf(section)), viewModel.uiState.value)
    }

    @Test
    fun onEpisodeClick_startsDriverPlayback() = runTest {
        val episode = sampleEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(
                        PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section))
                    )
                )
            ),
            markPodcastEpisodesPlayed = noopMark(),
            podcastPlayerDriver = playerDriver
        )
        advanceUntilIdle()

        viewModel.onEpisodeClick(episode)
        advanceUntilIdle()

        assertEquals(episode.id, playerDriver.playedEpisode?.id)
        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.LOADING, content.playerState.phase)
    }

    @Test
    fun nearEndPlayerState_marksEpisodePlayedOnceAndKeepsPlayerVisible() = runTest {
        val episode = sampleEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val catalogRepository = SwitchingCatalogRepository(
            first = PodcastCatalog(allEpisodes = listOf(episode), sections = listOf(section)),
            second = PodcastCatalog(allEpisodes = emptyList(), sections = emptyList())
        )
        val fileRepository = InMemoryPodcastFileRepository(
            mutableMapOf(
                "General/2026 News - podcasts.md" to
                    "- [ ] 2026-03-15; Episode title [\u25B6](https://cdn/episode.mp3) (Daily)\n"
            )
        )
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(catalogRepository),
            markPodcastEpisodesPlayed = MarkPodcastEpisodesPlayed(
                podcastFileRepository = fileRepository,
                syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) }
            ),
            podcastPlayerDriver = playerDriver
        )
        advanceUntilIdle()

        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.NEAR_END_PLAYING,
                positionMs = 51_000L,
                durationMs = 60_000L
            )
        )
        advanceUntilIdle()
        playerDriver.emit(
            PodcastPlaybackState(
                activeEpisode = episode,
                phase = PodcastPlaybackPhase.NEAR_END_PAUSED,
                positionMs = 52_000L,
                durationMs = 60_000L
            )
        )
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertTrue(content.sections.isEmpty())
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
        assertTrue(
            fileRepository.files["General/2026 News - podcasts.md"]
                ?.contains("- [x] 2026-03-15") == true
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

    private fun noopMark() = MarkPodcastEpisodesPlayed(
        podcastFileRepository = InMemoryPodcastFileRepository(mutableMapOf()),
        syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) }
    )

    private class FakePodcastCatalogRepository(private val result: Result<PodcastCatalog>) :
        PodcastCatalogRepository {
        override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
            result
    }

    private class SwitchingCatalogRepository(
        private val first: PodcastCatalog,
        private val second: PodcastCatalog
    ) : PodcastCatalogRepository {
        private var calls = 0
        override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> {
            calls += 1
            return Result.success(if (calls <= 1) first else second)
        }
    }

    private class InMemoryPodcastFileRepository(val files: MutableMap<String, String>) :
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

    private class FakePodcastPlayerDriver : PodcastPlayerDriver {
        private val mutableState = MutableStateFlow(PodcastPlaybackState())
        override val state: StateFlow<PodcastPlaybackState> = mutableState
        var playedEpisode: PodcastEpisode? = null
            private set

        override fun play(episode: PodcastEpisode) {
            playedEpisode = episode
            emit(
                PodcastPlaybackState(
                    activeEpisode = episode,
                    phase = PodcastPlaybackPhase.LOADING,
                    transportBusy = true
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
}
