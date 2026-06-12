package com.eskerra.go.data.vault

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.vault.EskerraSettingsCodec
import com.eskerra.go.core.vault.VaultLayout
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileVaultSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var dataStoreCount = 0

    private fun buildDataStore(): DataStore<Preferences> {
        val file = tmp.newFile("prefs-${dataStoreCount++}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { file }
        )
    }

    private fun localStore() = DataStoreLocalSettingsStore(buildDataStore())

    private fun repo(local: DataStoreLocalSettingsStore = localStore()) =
        FileVaultSettingsRepository(buildDataStore(), local, testDispatcher)

    private fun workspaceRoot() = tmp.newFolder("workspace")

    private fun eskerraDir(root: File) = File(root, VaultLayout.ESKERRA_DIR).also { it.mkdirs() }

    // ── load: file-based path ────────────────────────────────────────────────

    @Test
    fun `load reads shared settings file when present`() = testScope.runTest {
        val root = workspaceRoot()
        val dir = eskerraDir(root)
        val settings = EskerraSettings(r2 = r2())
        File(dir, VaultLayout.SHARED_SETTINGS_FILE)
            .writeText(EskerraSettingsCodec.serialize(settings))

        val loaded = repo().loadShared(root).getOrThrow()
        assertEquals(settings.r2, loaded.r2)
    }

    @Test
    fun `load migrates legacy settings dot json to shared file`() = testScope.runTest {
        val root = workspaceRoot()
        val dir = eskerraDir(root)
        val settings = EskerraSettings(r2 = r2())
        File(dir, VaultLayout.LEGACY_SETTINGS_FILE)
            .writeText(EskerraSettingsCodec.serialize(settings))

        val loaded = repo().loadShared(root).getOrThrow()
        assertEquals(settings.r2, loaded.r2)
        assertTrue(File(dir, VaultLayout.SHARED_SETTINGS_FILE).isFile)
    }

    @Test
    fun `load falls back to DataStore when no file exists`() = testScope.runTest {
        val root = workspaceRoot()
        val loaded = repo().loadShared(root).getOrThrow()
        assertNull(loaded.r2)
        assertTrue(loaded.extras.isEmpty())
    }

    @Test
    fun `load migrates notebox dir to eskerra dir`() = testScope.runTest {
        val root = workspaceRoot()
        val noteboxDir = File(root, VaultLayout.NOTEBOX_DIR).also { it.mkdirs() }
        val settings = EskerraSettings(r2 = r2())
        File(noteboxDir, VaultLayout.SHARED_SETTINGS_FILE)
            .writeText(EskerraSettingsCodec.serialize(settings))

        repo().loadShared(root).getOrThrow()

        assertFalse(noteboxDir.exists())
        assertTrue(File(root, VaultLayout.ESKERRA_DIR).isDirectory)
    }

    // ── save: file vs DataStore ──────────────────────────────────────────────

    @Test
    fun `save with R2 configured creates shared file`() = testScope.runTest {
        val root = workspaceRoot()
        val settings = EskerraSettings(r2 = r2())

        repo().saveShared(root, settings).getOrThrow()

        val sharedFile = File(root, VaultLayout.SHARED_SETTINGS_PATH)
        assertTrue(sharedFile.isFile)
        val reparsed = EskerraSettingsCodec.parse(sharedFile.readText()).getOrThrow()
        assertNotNull(reparsed.r2)
    }

    @Test
    fun `save without R2 and no existing file goes to DataStore`() = testScope.runTest {
        val root = workspaceRoot()
        val r = repo()
        r.saveShared(root, EskerraSettings()).getOrThrow()

        assertFalse(File(root, VaultLayout.SHARED_SETTINGS_PATH).isFile)
        val loaded = r.loadShared(root).getOrThrow()
        assertNull(loaded.r2)
    }

    @Test
    fun `save updates file when file already exists without R2`() = testScope.runTest {
        val root = workspaceRoot()
        val dir = eskerraDir(root)
        File(dir, VaultLayout.SHARED_SETTINGS_FILE)
            .writeText(EskerraSettingsCodec.serialize(EskerraSettings()))

        val r = repo()
        r.saveShared(root, EskerraSettings(r2 = r2())).getOrThrow()
        val loaded = r.loadShared(root).getOrThrow()
        assertNotNull(loaded.r2)
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    fun `round-trip via file preserves r2 and extras`() = testScope.runTest {
        val root = workspaceRoot()
        val original = EskerraSettings(r2 = r2())
        val r = repo()
        r.saveShared(root, original).getOrThrow()
        val loaded = r.loadShared(root).getOrThrow()
        assertEquals(original.r2, loaded.r2)
    }

    // ── migration: legacy shared displayName → local ─────────────────────────

    @Test
    fun `load migrates legacy shared displayName to local when local empty`() = testScope.runTest {
        val root = workspaceRoot()
        val dir = eskerraDir(root)
        File(dir, VaultLayout.SHARED_SETTINGS_FILE)
            .writeText("""{"displayName": "Alice", "r2": null}""")
        val local = localStore()

        repo(local).loadShared(root).getOrThrow()

        assertEquals("Alice", local.load().displayName)
        val rewritten = File(dir, VaultLayout.SHARED_SETTINGS_FILE).readText()
        assertFalse(rewritten.contains("displayName"))
    }

    @Test
    fun `load does not overwrite a non-empty local displayName`() = testScope.runTest {
        val root = workspaceRoot()
        val dir = eskerraDir(root)
        File(dir, VaultLayout.SHARED_SETTINGS_FILE)
            .writeText("""{"displayName": "Alice", "r2": null}""")
        val local = localStore()
        local.save(EskerraLocalSettings(displayName = "Bob"))

        repo(local).loadShared(root).getOrThrow()

        assertEquals("Bob", local.load().displayName)
        val rewritten = File(dir, VaultLayout.SHARED_SETTINGS_FILE).readText()
        assertFalse(rewritten.contains("displayName"))
    }

    private fun r2() = R2Config(
        endpoint = "https://abc.r2.cloudflarestorage.com",
        bucket = "bucket",
        accessKeyId = "key",
        secretAccessKey = "secret"
    )
}
