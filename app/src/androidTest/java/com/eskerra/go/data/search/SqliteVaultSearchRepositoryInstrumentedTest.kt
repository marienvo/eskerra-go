package com.eskerra.go.data.search

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteVaultSearchRepositoryInstrumentedTest {

    private lateinit var filesDir: File
    private lateinit var repository: SqliteVaultSearchRepository
    private val config = WorkspaceConfig(
        name = "Test",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 0L
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        filesDir = File(context.cacheDir, "vault-search-it-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        repository = SqliteVaultSearchRepository(context)
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).apply { mkdirs() }
        File(workspace, "Inbox").mkdirs()
        File(workspace, "Inbox/alpha.md").writeText("# Alpha\n\nalpha body text")
        File(workspace, "Inbox/beta.md").writeText("# Beta\n\nother content")
    }

    @Test
    fun maintain_bootstrapsEmptyDatabaseFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).apply { mkdirs() }
        val dbFile = VaultSearchPathHasher.indexDatabaseFile(filesDir, workspace.canonicalFile)
        dbFile.parentFile?.mkdirs()
        check(dbFile.createNewFile()) { "expected empty database file" }

        val maintain = MaintainVaultSearchIndex(repository)
        assertTrue(maintain(config, filesDir).isSuccess)

        val helper = VaultSearchDatabase(context, dbFile)
        assertTrue(VaultSearchDatabase.hasTable(helper.ensureOpen(), "index_meta"))
        helper.close()
    }

    @Test
    fun maintainAndSearch_findsTitleMatch() = runBlocking {
        val maintain = MaintainVaultSearchIndex(repository)
        val search = SearchVault(repository)

        assertTrue(maintain(config, filesDir).isSuccess)

        val outcome = search(config, filesDir, "alpha", searchId = 1L).getOrThrow()
        assertTrue(outcome.notes.isNotEmpty())
        assertEquals("Alpha", outcome.notes.first().title)
        assertTrue(outcome.status.indexReady)
    }

    @Test
    fun repairIndex_rebuildsAfterDeletingDatabase() = runBlocking {
        val maintain = MaintainVaultSearchIndex(repository)
        val repair = RepairVaultSearchIndex(repository)
        val search = SearchVault(repository)

        maintain(config, filesDir).getOrThrow()
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        val dbFile = VaultSearchPathHasher.indexDatabaseFile(filesDir, workspace.canonicalFile)
        assertTrue(dbFile.exists())
        dbFile.delete()

        assertTrue(repair(config, filesDir).isSuccess)
        assertTrue(maintain(config, filesDir).isSuccess)

        val outcome = search(config, filesDir, "beta", searchId = 2L).getOrThrow()
        assertTrue(outcome.notes.any { it.title == "Beta" })
    }
}
