package com.eskerra.go.app

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.VaultSettingsRepository
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.feature.sync.VaultSettingsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VaultSettingsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sharedStore: MutableMap<File, EskerraSettings>
    private lateinit var localStore: FakeLocalSettingsStore
    private lateinit var vm: VaultSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sharedStore = mutableMapOf()
        localStore = FakeLocalSettingsStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(initial: EskerraSettings = EskerraSettings()): VaultSettingsViewModel {
        val root = tmp.newFolder("workspace")
        sharedStore[root] = initial
        val config = WorkspaceConfig(
            name = "test",
            relativePath = "workspace",
            remoteUri = null,
            branch = "main",
            setupCompletedAtEpochMs = 0L
        )
        val fakeRepo = object : VaultSettingsRepository {
            override suspend fun loadShared(workspaceRoot: File): Result<EskerraSettings> =
                Result.success(sharedStore[workspaceRoot] ?: EskerraSettings())

            override suspend fun saveShared(
                workspaceRoot: File,
                settings: EskerraSettings
            ): Result<Unit> {
                sharedStore[workspaceRoot] = settings
                return Result.success(Unit)
            }
        }
        return VaultSettingsViewModel(
            config = config,
            filesDir = tmp.root,
            loadVaultSettings = LoadVaultSettings(fakeRepo),
            saveVaultSettings = SaveVaultSettings(fakeRepo),
            loadLocalSettings = LoadLocalSettings(localStore),
            saveLocalSettings = SaveLocalSettings(localStore),
            ensureDeviceInstanceId = EnsureDeviceInstanceId(localStore)
        )
    }

    @Test
    fun `initial state is Loading then becomes Ready`() = runTest {
        vm = buildVm()
        val initial = vm.uiState.first()
        assertEquals(VaultSettingsUiState.Loading, initial)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.first() is VaultSettingsUiState.Ready)
    }

    @Test
    fun `ready state reflects loaded settings`() = runTest {
        vm = buildVm(
            EskerraSettings(
                r2 = R2Config("https://e.com", "bucket", "key", "secret", R2Jurisdiction.Eu)
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val ready = vm.uiState.first() as VaultSettingsUiState.Ready
        assertEquals("https://e.com", ready.r2Endpoint)
        assertEquals(R2Jurisdiction.Eu, ready.r2Jurisdiction)
        assertEquals("bucket", ready.r2Bucket)
    }

    @Test
    fun `save with all R2 fields produces success status`() = runTest {
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onR2EndpointChange("https://e.com")
        vm.onR2BucketChange("b")
        vm.onR2AccessKeyIdChange("k")
        vm.onR2SecretAccessKeyChange("s")
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.uiState.first() as VaultSettingsUiState.Ready
        assertEquals("Settings saved.", ready.statusMessage)
        assertNull(ready.errorMessage)
    }

    @Test
    fun `save with partial R2 fields shows exact error message`() = runTest {
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onR2EndpointChange("https://e.com")
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.uiState.first() as VaultSettingsUiState.Ready
        assertEquals(
            "Complete all Cloudflare R2 fields or clear them all.",
            ready.errorMessage
        )
        assertNull(ready.statusMessage)
    }

    @Test
    fun `save all empty R2 fields clears r2 from stored settings`() = runTest {
        vm = buildVm(EskerraSettings(r2 = R2Config("https://e.com", "b", "k", "s")))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onR2EndpointChange("")
        vm.onR2BucketChange("")
        vm.onR2AccessKeyIdChange("")
        vm.onR2SecretAccessKeyChange("")
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.uiState.first() as VaultSettingsUiState.Ready
        assertEquals("Settings saved.", ready.statusMessage)
        assertTrue(ready.r2Endpoint.isEmpty())
    }

    @Test
    fun `save persists display name and device name trimmed`() = runTest {
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onDisplayNameChange("  Alice  ")
        vm.onDeviceNameChange("  Phone  ")
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Alice", localStore.stored.displayName)
        assertEquals("Phone", localStore.stored.deviceName)
    }

    @Test
    fun `save shows error when local settings persist fails`() = runTest {
        localStore = FailingLocalSettingsStore()
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.uiState.first() as VaultSettingsUiState.Ready
        assertEquals("disk full", ready.errorMessage)
        assertNull(ready.statusMessage)
    }

    @Test
    fun `save preserves deviceInstanceId across save`() = runTest {
        localStore.stored = EskerraLocalSettings(deviceInstanceId = "existing-id")
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("existing-id", localStore.stored.deviceInstanceId)
    }

    @Test
    fun `save preserves playlist watermarks across save`() = runTest {
        localStore.stored = EskerraLocalSettings(playlistKnownUpdatedAtMs = 9999L)
        vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(9999L, localStore.stored.playlistKnownUpdatedAtMs)
    }

    @Test
    fun `previousShared extras are preserved on save`() = runTest {
        val themeKey = "themePreference"
        val withExtras = kotlinx.serialization.json.buildJsonObject {
            put(themeKey, kotlinx.serialization.json.JsonPrimitive("dark"))
        }
        val initial = EskerraSettings(
            extras = mapOf(themeKey to withExtras[themeKey]!!)
        )
        vm = buildVm(initial)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val savedSettings = sharedStore.values.first()
        assertNotNull(savedSettings.extras[themeKey])
    }

    private open class FakeLocalSettingsStore(
        var stored: EskerraLocalSettings = EskerraLocalSettings()
    ) : LocalSettingsStore {
        override suspend fun load(): EskerraLocalSettings = stored
        override suspend fun save(settings: EskerraLocalSettings) {
            stored = settings
        }
    }

    private class FailingLocalSettingsStore :
        FakeLocalSettingsStore(EskerraLocalSettings(deviceInstanceId = "existing-id")) {
        override suspend fun save(settings: EskerraLocalSettings) =
            throw IllegalStateException("disk full")
    }
}
