package com.eskerra.go

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eskerra.go.app.AppRoot
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.PrefetchLinkedNotes
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.data.credentials.AndroidKeystoreTokenCipher
import com.eskerra.go.data.credentials.EncryptedCredentialStore
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.CoalescingNoteRegistryRepository
import com.eskerra.go.data.notes.FileInboxSnapshotStore
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteRegistrySnapshotStore
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.search.SqliteVaultSearchRepository
import com.eskerra.go.data.todayhub.DataStoreActiveTodayHubStore
import com.eskerra.go.data.todayhub.FileTodayHubSnapshotStore
import com.eskerra.go.data.vault.DataStoreLocalSettingsStore
import com.eskerra.go.data.vault.FileVaultSettingsRepository
import com.eskerra.go.data.workspace.DataStoreWorkspaceStore
import com.eskerra.go.data.workspace.DefaultRemoteSyncSettingsRepository
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupCompletion
import com.eskerra.go.data.workspace.DefaultWorkspaceSetupRepository

/** Single entry point. Hosts the Compose UI and nothing else. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        var keepSplashOnScreen by mutableStateOf(true)
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val workspaceStore = DataStoreWorkspaceStore(applicationContext)
        val bootCacheStore = workspaceStore
        val credentialStore = EncryptedCredentialStore(
            filesDir = filesDir,
            tokenCipher = AndroidKeystoreTokenCipher()
        )
        val gitRepository = JGitWorkspaceRepository()
        val setupCompletion = DefaultWorkspaceSetupCompletion(
            setupRepository = DefaultWorkspaceSetupRepository(gitRepository),
            workspaceStore = workspaceStore,
            credentialStore = credentialStore
        )

        val fileNoteRegistryRepository = FileNoteRegistryRepository()
        val noteRegistryRepository = CoalescingNoteRegistryRepository(fileNoteRegistryRepository)
        val noteContentCache = NoteContentCache(FileNoteContentRepository())
        val noteWriteRepository = FileNoteWriteRepository(gitRepository)
        val loadGitStatusSummary = LoadGitStatusSummary(gitRepository)

        val noteRegistryCache = NoteRegistryCache(
            repository = noteRegistryRepository,
            snapshotStore = FileNoteRegistrySnapshotStore()
        )

        val loadInboxSummaries = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(noteRegistryCache),
            snapshotStore = FileInboxSnapshotStore()
        )
        val loadNoteForReading = LoadNoteForReading(
            registryCache = noteRegistryCache,
            contentRepository = noteContentCache
        )
        val prefetchLinkedNotes = PrefetchLinkedNotes(contentCache = noteContentCache)
        val createInboxNote = CreateInboxNote(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary
        )
        val deleteInboxNotes = DeleteInboxNotes(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary
        )
        val loadEditableNote = LoadEditableNote(
            registryRepository = noteRegistryRepository,
            contentRepository = noteContentCache
        )
        val saveNote = SaveNote(
            writeRepository = noteWriteRepository,
            registryCache = noteRegistryCache,
            loadGitStatusSummary = loadGitStatusSummary,
            contentCache = noteContentCache
        )

        val loadTodayHub = LoadTodayHub(
            registryCache = noteRegistryCache,
            contentRepository = noteContentCache
        )
        val loadTodayHubRow = LoadTodayHubRow(contentRepository = noteContentCache)
        val activeTodayHubStore = DataStoreActiveTodayHubStore(applicationContext)
        val todayHubSnapshotStore = FileTodayHubSnapshotStore()

        val remoteSyncRepository = JGitRemoteSyncRepository(gitRepository)
        val loadSyncStatus = LoadSyncStatus(remoteSyncRepository)
        val refreshRemoteSyncStatus = RefreshRemoteSyncStatus(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            loadSyncStatus = loadSyncStatus
        )
        val buildSyncPreflight = BuildSyncPreflight(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore
        )
        val buildSafeSyncDiagnostic = BuildSafeSyncDiagnostic(
            buildSyncPreflight = buildSyncPreflight,
            lastSyncStatusStore = workspaceStore
        )
        val recordLastSyncAttempt = RecordLastSyncAttempt(workspaceStore)
        val reconcileWorkspaceSyncBranch = ReconcileWorkspaceSyncBranch(
            workspaceStore = workspaceStore,
            credentialStore = credentialStore,
            remoteSyncRepository = remoteSyncRepository
        )
        val manualSyncNow = ManualSyncNow(
            remoteSyncRepository = remoteSyncRepository,
            credentialStore = credentialStore,
            registryCache = noteRegistryCache,
            contentCache = noteContentCache,
            loadSyncStatus = loadSyncStatus,
            reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch
        )

        val localSettingsStore = DataStoreLocalSettingsStore(applicationContext)
        val vaultSettingsRepository = FileVaultSettingsRepository(
            applicationContext,
            localSettingsStore
        )
        val loadVaultSettings = LoadVaultSettings(vaultSettingsRepository)
        val saveVaultSettings = SaveVaultSettings(vaultSettingsRepository)
        val loadLocalSettings = LoadLocalSettings(localSettingsStore)
        val saveLocalSettings = SaveLocalSettings(localSettingsStore)
        val ensureDeviceInstanceId = EnsureDeviceInstanceId(localSettingsStore)

        val remoteSyncSettingsRepository = DefaultRemoteSyncSettingsRepository(
            workspaceStore = workspaceStore,
            credentialStore = credentialStore,
            remoteSyncRepository = remoteSyncRepository
        )
        val loadRemoteSyncSettings = LoadRemoteSyncSettings(remoteSyncSettingsRepository)
        val saveRemoteSyncSettings = SaveRemoteSyncSettings(remoteSyncSettingsRepository)
        val updateSyncToken = UpdateSyncToken(remoteSyncSettingsRepository)
        val clearRemoteSyncSettings = ClearRemoteSyncSettings(remoteSyncSettingsRepository)
        val testRemoteConnection = TestRemoteConnection(remoteSyncSettingsRepository)

        val vaultSearchRepository = SqliteVaultSearchRepository(applicationContext)
        val searchVault = SearchVault(vaultSearchRepository)
        val maintainVaultSearchIndex = MaintainVaultSearchIndex(vaultSearchRepository)
        val repairVaultSearchIndex = RepairVaultSearchIndex(vaultSearchRepository)
        val touchVaultSearchPaths = TouchVaultSearchPaths(vaultSearchRepository)

        setContent {
            AppRoot(
                workspaceStore = workspaceStore,
                bootCacheStore = bootCacheStore,
                setupCompletion = setupCompletion,
                filesDir = filesDir,
                loadInboxSummaries = loadInboxSummaries,
                loadNoteForReading = loadNoteForReading,
                prefetchLinkedNotes = prefetchLinkedNotes,
                createInboxNote = createInboxNote,
                deleteInboxNotes = deleteInboxNotes,
                loadEditableNote = loadEditableNote,
                saveNote = saveNote,
                loadGitStatusSummary = loadGitStatusSummary,
                loadTodayHub = loadTodayHub,
                loadTodayHubRow = loadTodayHubRow,
                activeTodayHubStore = activeTodayHubStore,
                todayHubSnapshotStore = todayHubSnapshotStore,
                loadSyncStatus = loadSyncStatus,
                refreshRemoteSyncStatus = refreshRemoteSyncStatus,
                buildSyncPreflight = buildSyncPreflight,
                buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
                manualSyncNow = manualSyncNow,
                recordLastSyncAttempt = recordLastSyncAttempt,
                loadRemoteSyncSettings = loadRemoteSyncSettings,
                saveRemoteSyncSettings = saveRemoteSyncSettings,
                updateSyncToken = updateSyncToken,
                clearRemoteSyncSettings = clearRemoteSyncSettings,
                testRemoteConnection = testRemoteConnection,
                reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
                loadVaultSettings = loadVaultSettings,
                saveVaultSettings = saveVaultSettings,
                loadLocalSettings = loadLocalSettings,
                saveLocalSettings = saveLocalSettings,
                ensureDeviceInstanceId = ensureDeviceInstanceId,
                searchVault = searchVault,
                maintainVaultSearchIndex = maintainVaultSearchIndex,
                repairVaultSearchIndex = repairVaultSearchIndex,
                touchVaultSearchPaths = touchVaultSearchPaths,
                onLaunchSettled = {
                    if (keepSplashOnScreen) {
                        keepSplashOnScreen = false
                    }
                }
            )
        }
    }
}
