package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.BinaryManifestEntry
import com.eskerra.go.core.model.BinarySyncSummary
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2BinaryObject
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.repository.BinarySyncRepository
import com.eskerra.go.core.repository.VaultSettingsRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBinariesTest {

    private val root = File("/tmp/workspace")

    private fun r2() = R2Config(
        endpoint = "https://acc.r2.cloudflarestorage.com",
        bucket = "vault",
        accessKeyId = "key",
        secretAccessKey = "secret"
    )

    private fun useCase(repo: BinarySyncRepository, settings: EskerraSettings): SyncBinaries =
        SyncBinaries(repo, LoadVaultSettings(FakeVaultSettings(settings)))

    @Test
    fun `not configured when r2 absent`() = runBlocking {
        val summary = useCase(FakeBinaryRepo(), EskerraSettings(r2 = null)).invoke(root)
        assertEquals(BinarySyncSummary.NotConfigured, summary)
    }

    @Test
    fun `downloads gitignored objects and skips others`() = runBlocking {
        val repo = FakeBinaryRepo(
            remote = mutableListOf(
                R2BinaryObject("binaries/a.bin", 10, "e1"),
                R2BinaryObject("binaries/b.bin", 20, "e2")
            ),
            ignored = mutableSetOf("a.bin")
        )
        val summary = useCase(repo, EskerraSettings(r2 = r2())).invoke(root)

        assertEquals(BinarySyncSummary.Completed(downloaded = 1, deleted = 0, skipped = 1), summary)
        assertEquals(listOf("a.bin"), repo.downloaded)
        assertEquals(listOf("a.bin"), repo.written?.map { it.relPath })
    }

    @Test
    fun `skips objects already up to date`() = runBlocking {
        val repo = FakeBinaryRepo(
            remote = mutableListOf(R2BinaryObject("binaries/a.bin", 10, "e1")),
            ignored = mutableSetOf("a.bin"),
            manifest = mutableListOf(BinaryManifestEntry("a.bin", "binaries/a.bin", 10, "e1")),
            localSizes = mutableMapOf("a.bin" to 10L)
        )
        val summary = useCase(repo, EskerraSettings(r2 = r2())).invoke(root)

        assertEquals(BinarySyncSummary.Completed(downloaded = 0, deleted = 0, skipped = 0), summary)
        assertTrue(repo.downloaded.isEmpty())
    }

    @Test
    fun `deletes local files removed from r2`() = runBlocking {
        val repo = FakeBinaryRepo(
            remote = mutableListOf(),
            manifest = mutableListOf(BinaryManifestEntry("a.bin", "binaries/a.bin", 10, "e1")),
            localSizes = mutableMapOf("a.bin" to 10L)
        )
        val summary = useCase(repo, EskerraSettings(r2 = r2())).invoke(root)

        assertEquals(BinarySyncSummary.Completed(downloaded = 0, deleted = 1, skipped = 0), summary)
        assertEquals(listOf("a.bin"), repo.deleted)
        assertTrue(repo.written?.isEmpty() == true)
    }

    @Test
    fun `re-downloads when etag changed`() = runBlocking {
        val repo = FakeBinaryRepo(
            remote = mutableListOf(R2BinaryObject("binaries/a.bin", 10, "e2")),
            ignored = mutableSetOf("a.bin"),
            manifest = mutableListOf(BinaryManifestEntry("a.bin", "binaries/a.bin", 10, "e1")),
            localSizes = mutableMapOf("a.bin" to 10L)
        )
        val summary = useCase(repo, EskerraSettings(r2 = r2())).invoke(root)

        assertEquals(BinarySyncSummary.Completed(downloaded = 1, deleted = 0, skipped = 0), summary)
        assertEquals(listOf("a.bin"), repo.downloaded)
    }
}

private class FakeVaultSettings(private val settings: EskerraSettings) : VaultSettingsRepository {
    override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> =
        Result.success(settings)

    override suspend fun saveShared(workspaceRoot: File, settings: EskerraSettings): Result<Unit> =
        Result.success(Unit)
}

private class FakeBinaryRepo(
    private val remote: MutableList<R2BinaryObject> = mutableListOf(),
    private val ignored: MutableSet<String> = mutableSetOf(),
    private var manifest: MutableList<BinaryManifestEntry> = mutableListOf(),
    private val localSizes: MutableMap<String, Long> = mutableMapOf()
) : BinarySyncRepository {

    val downloaded = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var written: List<BinaryManifestEntry>? = null

    override suspend fun listRemoteBinaries(config: R2Config): List<R2BinaryObject> = remote

    override suspend fun retainIgnored(workspaceRoot: File, relPaths: List<String>): List<String> =
        relPaths.filter { it in ignored }

    override suspend fun downloadBinary(
        config: R2Config,
        key: String,
        workspaceRoot: File,
        relPath: String
    ) {
        downloaded += relPath
        localSizes[relPath] = remote.first { it.key == key }.size
    }

    override suspend fun deleteLocalBinary(workspaceRoot: File, relPath: String) {
        deleted += relPath
        localSizes.remove(relPath)
    }

    override suspend fun readManifest(): List<BinaryManifestEntry> = manifest.toList()

    override suspend fun writeManifest(entries: List<BinaryManifestEntry>) {
        written = entries
        manifest = entries.toMutableList()
    }

    override fun localSize(workspaceRoot: File, relPath: String): Long? = localSizes[relPath]
}
