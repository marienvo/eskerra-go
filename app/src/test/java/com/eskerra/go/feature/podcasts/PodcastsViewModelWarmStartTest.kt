package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Bootstrap-timing: warm-start stale-catalog paint from the snapshot store (spec §6.2). */
class PodcastsViewModelWarmStartTest {

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
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun warmStart_paintsSnapshotBeforeReloadCompletes() = runTest {
        val cached = PodcastSection(
            episodes = listOf(samplePodcastEpisode()),
            rssFeedUrl = null,
            title = "Cached"
        )
        val gate = CompletableDeferred<Result<PodcastCatalog>>()
        val viewModel = buildViewModel(
            loadPodcastCatalog = LoadPodcastCatalog(GatedCatalogRepository(gate)),
            snapshot = PodcastCatalog(allEpisodes = emptyList(), sections = listOf(cached))
        )

        // Reload is still suspended on the gate; only the snapshot has resolved.
        runCurrent()
        val stale = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(listOf("Cached"), stale.sections.map { it.title })

        val fresh = PodcastSection(emptyList(), rssFeedUrl = null, title = "Fresh")
        gate.complete(Result.success(PodcastCatalog(emptyList(), listOf(fresh))))
        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(listOf("Fresh"), content.sections.map { it.title })
    }

    @Test
    fun warmStart_doesNotOverrideCompletedReload() = runTest {
        val fresh = PodcastSection(emptyList(), rssFeedUrl = null, title = "Fresh")
        val viewModel = buildViewModel(
            loadPodcastCatalog = LoadPodcastCatalog(
                FakePodcastCatalogRepository(
                    Result.success(PodcastCatalog(emptyList(), listOf(fresh)))
                )
            ),
            snapshot = PodcastCatalog(
                allEpisodes = emptyList(),
                sections = listOf(PodcastSection(emptyList(), null, "Cached"))
            )
        )

        advanceUntilIdle()

        val content = viewModel.uiState.value as PodcastsUiState.Content
        assertEquals(listOf("Fresh"), content.sections.map { it.title })
    }

    private fun buildViewModel(loadPodcastCatalog: LoadPodcastCatalog, snapshot: PodcastCatalog?) =
        podcastsViewModelPersistenceDefaults().let { persistence ->
            PodcastsViewModel(
                config = config,
                filesDir = temp.newFolder("files"),
                loadPodcastCatalog = loadPodcastCatalog,
                markPodcastEpisodesPlayed = noopMarkPodcastEpisodesPlayed(),
                podcastPlaylistSync = noopPodcastPlaylistSync(),
                podcastPlayerDriver = FakePodcastPlayerDriver(),
                syncPodcastVaultRefresh = noopSyncPodcastVaultRefresh(),
                loadPodcastArtwork = noopLoadPodcastArtwork(),
                catalogSnapshotStore = FakeSnapshotStore(snapshot),
                persistPodcastPlaybackSnapshot = persistence.persistPodcastPlaybackSnapshot,
                clearPodcastPlaybackSnapshot = persistence.clearPodcastPlaybackSnapshot,
                loadLocalSettings = persistence.loadLocalSettings
            )
        }

    private class GatedCatalogRepository(
        private val gate: CompletableDeferred<Result<PodcastCatalog>>
    ) : PodcastCatalogRepository {
        override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
            gate.await()
    }

    private class FakeSnapshotStore(private val snapshot: PodcastCatalog?) :
        PodcastCatalogSnapshotStore {
        override suspend fun read(config: WorkspaceConfig, filesDir: File): PodcastCatalog? =
            snapshot

        override suspend fun save(
            config: WorkspaceConfig,
            filesDir: File,
            catalog: PodcastCatalog
        ) = Unit

        override suspend fun clear(config: WorkspaceConfig, filesDir: File) = Unit
    }
}
