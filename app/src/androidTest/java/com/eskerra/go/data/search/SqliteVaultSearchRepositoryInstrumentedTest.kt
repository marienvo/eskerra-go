package com.eskerra.go.data.search

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun maintain_autoRepairsCorruptDatabase() = runBlocking {
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        val dbFile = VaultSearchPathHasher.indexDatabaseFile(filesDir, workspace.canonicalFile)
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

        val maintain = MaintainVaultSearchIndex(repository)
        val search = SearchVault(repository)

        assertTrue(maintain(config, filesDir).isSuccess)

        val outcome = search(config, filesDir, "alpha", searchId = 3L).getOrThrow()
        assertTrue(outcome.notes.isNotEmpty())
        assertEquals("Alpha", outcome.notes.first().title)
    }

    @Test
    fun status_reportsPendingBodies_afterIncrementalAdds() = runBlocking {
        val maintain = MaintainVaultSearchIndex(repository)
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        val inbox = File(workspace, "Inbox")

        maintain(config, filesDir).getOrThrow()
        assertTrue(repository.status(config, filesDir).bodiesIndexReady)

        repeat(105) { index ->
            File(inbox, "extra-$index.md").writeText("# Extra $index\n\nbody $index")
        }
        maintain(config, filesDir).getOrThrow()

        val status = repository.status(config, filesDir)
        assertTrue(status.indexReady)
        assertFalse(status.bodiesIndexReady)
    }

    @Test
    fun touchPaths_prioritizesUpdatedNoteBody_overBacklog() = runBlocking {
        val maintain = MaintainVaultSearchIndex(repository)
        val touch = TouchVaultSearchPaths(repository)
        val search = SearchVault(repository)
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        val inbox = File(workspace, "Inbox")

        repeat(201) { index ->
            File(inbox, "bulk-$index.md").writeText("# Bulk $index\n\nbulk body $index")
        }
        maintain(config, filesDir).getOrThrow()

        val touchedPath = "Inbox/touched.md"
        val touchedFile = File(inbox, "touched.md")
        touchedFile.writeText("# Touched\n\nprioritytouchxyz unique body")
        touch(config, filesDir, listOf(touchedPath)).getOrThrow()

        val outcome = search(config, filesDir, "prioritytouchxyz", searchId = 4L).getOrThrow()
        assertTrue(outcome.notes.any { it.title == "Touched" })
    }
}
