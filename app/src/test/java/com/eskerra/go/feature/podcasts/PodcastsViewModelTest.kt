package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastRefreshProgress
import com.eskerra.go.core.repository.PodcastRssVaultSync
import com.eskerra.go.core.repository.PodcastRssVaultSyncSummary
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PodcastsViewModelTest {

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
    fun init_showsContentWhenCatalogHasSections() = runTest {
        val episode = samplePodcastEpisode()
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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
        val episode = samplePodcastEpisode()
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
        )
        advanceUntilIdle()

        viewModel.markEpisodesPlayed(emptyList())
        advanceUntilIdle()

        assertEquals(PodcastsUiState.Content(sections = listOf(section)), viewModel.uiState.value)
    }

    @Test
    fun onEpisodeClick_startsDriverPlayback() = runTest {
        val episode = samplePodcastEpisode()
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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
        val playerDriver = FakePodcastPlayerDriver()
        val viewModel = PodcastsViewModel(
            config = config,
            filesDir = temp.newFolder("files"),
            loadPodcastCatalog = LoadPodcastCatalog(catalogRepository),
            markPodcastEpisodesPlayed = MarkPodcastEpisodesPlayed(
                podcastFileRepository = fileRepository,
                syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) }
            ),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
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

    @Test
    fun restoreOnce_hydratesSavedPlaylistEpisode() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val playerDriver = FakePodcastPlayerDriver()
        val playlistEntry = PlaylistEntry(
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            positionMs = 12_000L,
            durationMs = 60_000L
        )
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
            podcastPlaylistSync = podcastPlaylistSyncForTest(
                readEntry = playlistEntry,
                r2Configured = true
            ),
            podcastPlayerDriver = playerDriver,
            syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
        )

        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(PodcastPlaybackPhase.PRIMED, content.playerState.phase)
        assertEquals(12_000L, content.playerState.positionMs)
        assertEquals(episode.id, content.playerState.activeEpisode?.id)
    }

    @Test
    fun runVaultRefresh_invokesVaultSyncAndSetsActiveState() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val refreshCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val syncPodcastVaultRefresh = SyncPodcastVaultRefresh(
            vaultSync = object : PodcastRssVaultSync {
                override suspend fun refresh(
                    config: WorkspaceConfig,
                    filesDir: File,
                    onProgress: (PodcastRefreshProgress) -> Unit
                ): Result<PodcastRssVaultSyncSummary> {
                    refreshCount.incrementAndGet()
                    onProgress(PodcastRefreshProgress(50, PodcastRefreshProgress.PHASE_RSS))
                    gate.await()
                    return Result.success(PodcastRssVaultSyncSummary(1, 0, 0))
                }
            },
            syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) },
            dispatcher = dispatcher
        )
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = syncPodcastVaultRefresh,
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
        )
        advanceUntilIdle()

        viewModel.runVaultRefresh()
        assertTrue(viewModel.refreshState.value.active)

        advanceUntilIdle()
        assertEquals(50, viewModel.refreshState.value.percent)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.refreshState.value.active)
        assertNull(viewModel.refreshState.value.error)
        assertEquals(1, refreshCount.get())
    }

    @Test
    fun runVaultRefresh_whileActive_isIgnored() = runTest {
        val episode = samplePodcastEpisode()
        val section = PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        val refreshCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val syncPodcastVaultRefresh = SyncPodcastVaultRefresh(
            vaultSync = object : PodcastRssVaultSync {
                override suspend fun refresh(
                    config: WorkspaceConfig,
                    filesDir: File,
                    onProgress: (PodcastRefreshProgress) -> Unit
                ): Result<PodcastRssVaultSyncSummary> {
                    refreshCount.incrementAndGet()
                    gate.await()
                    return Result.success(PodcastRssVaultSyncSummary.EMPTY)
                }
            },
            syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) },
            dispatcher = dispatcher
        )
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
            markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
            podcastPlaylistSync = noopPodcastPlaylistSync(),
            podcastPlayerDriver = FakePodcastPlayerDriver(),
            syncPodcastVaultRefresh = syncPodcastVaultRefresh,
            loadPodcastArtwork = noopLoadPodcastArtwork(),
            persistPodcastPlaybackSnapshot = persistence().persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = persistence().clearPodcastPlaybackSnapshot,
            loadLocalSettings = persistence().loadLocalSettings
        )
        advanceUntilIdle()

        viewModel.runVaultRefresh()
        advanceUntilIdle()
        assertTrue(viewModel.refreshState.value.active)

        viewModel.runVaultRefresh()
        advanceUntilIdle()
        assertEquals(1, refreshCount.get())

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.refreshState.value.active)
    }
}
