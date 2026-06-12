package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.SearchOutcome
import com.eskerra.go.core.repository.VaultSearchRepository
import com.eskerra.go.core.search.VaultSearchBestField
import com.eskerra.go.core.search.VaultSearchIndexStatus
import com.eskerra.go.core.search.VaultSearchNoteResult
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.search.SearchUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var filesDir: File
    private val config = WorkspaceConfig(
        name = "Test",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 0L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        filesDir = File.createTempFile("search-vm", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        filesDir.deleteRecursively()
    }

    @Test
    fun debouncesQueryBeforeSearching() = runTest(dispatcher) {
        val repository = FakeVaultSearchRepository()
        val viewModel = SearchViewModel(
            config = config,
            filesDir = filesDir,
            searchVault = SearchVault(repository),
            maintainVaultSearchIndex = MaintainVaultSearchIndex(repository)
        )

        viewModel.onQueryChange("alpha")
        advanceTimeBy(200)
        assertTrue(
            viewModel.uiState.value is SearchUiState.Opening ||
                viewModel.uiState.value is SearchUiState.Idle
        )

        advanceTimeBy(200)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            viewModel.uiState.value is SearchUiState.Results ||
                viewModel.uiState.value is SearchUiState.NoMatches
        )
    }

    @Test
    fun emptyQueryReturnsIdle() = runTest(dispatcher) {
        val repository = FakeVaultSearchRepository()
        val viewModel = SearchViewModel(
            config = config,
            filesDir = filesDir,
            searchVault = SearchVault(repository),
            maintainVaultSearchIndex = MaintainVaultSearchIndex(repository)
        )

        viewModel.onQueryChange("note")
        advanceTimeBy(400)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChange("")
        advanceTimeBy(400)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    private class FakeVaultSearchRepository : VaultSearchRepository {
        override suspend fun status(
            config: WorkspaceConfig,
            filesDir: File
        ): VaultSearchIndexStatus =
            VaultSearchIndexStatus("vault-1", indexReady = true, bodiesIndexReady = true)

        override suspend fun maintain(config: WorkspaceConfig, filesDir: File): Result<Unit> =
            Result.success(Unit)

        override suspend fun touchPaths(
            config: WorkspaceConfig,
            filesDir: File,
            paths: List<String>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun search(
            config: WorkspaceConfig,
            filesDir: File,
            query: String,
            searchId: Long
        ): Result<SearchOutcome> = Result.success(
            SearchOutcome(
                searchId = searchId,
                vaultInstanceId = "vault-1",
                notes = listOf(
                    VaultSearchNoteResult(
                        uri = "Inbox/a.md",
                        relativePath = "Inbox/a.md",
                        title = "Alpha",
                        bestField = VaultSearchBestField.TITLE,
                        matchCount = 1,
                        score = 40_000f,
                        snippets = emptyList()
                    )
                ),
                status = VaultSearchIndexStatus("vault-1", true, true)
            )
        )
    }
}
