package com.eskerra.go.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.BootCacheStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
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
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.data.notes.ParsedMarkdownCache
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceStore
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.setup.WorkspaceSetupScreen
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.LocalParsedMarkdownCache
import com.eskerra.go.ui.theme.EskerraGoTheme
import java.io.File

/**
 * Root gate: Loading, workspace setup, or the main app shell. Setup is not a
 * NavHost route.
 */
@Composable
fun AppRoot(
    workspaceStore: WorkspaceStore,
    bootCacheStore: BootCacheStore,
    setupCompletion: WorkspaceSetupCompletion,
    filesDir: File,
    parsedMarkdownCache: ParsedMarkdownCache,
    loadInboxSummaries: LoadInboxSummariesCached,
    loadNoteForReading: LoadNoteForReading,
    prefetchLinkedNotes: PrefetchLinkedNotes,
    createInboxNote: CreateInboxNote,
    deleteInboxNotes: DeleteInboxNotes,
    loadEditableNote: LoadEditableNote,
    saveNote: SaveNote,
    loadGitStatusSummary: LoadGitStatusSummary,
    loadTodayHub: LoadTodayHub,
    loadTodayHubRow: LoadTodayHubRow,
    activeTodayHubStore: ActiveTodayHubStore,
    todayHubSnapshotStore: TodayHubSnapshotStore,
    loadSyncStatus: LoadSyncStatus,
    refreshRemoteSyncStatus: RefreshRemoteSyncStatus,
    buildSyncPreflight: BuildSyncPreflight,
    buildSafeSyncDiagnostic: BuildSafeSyncDiagnostic,
    manualSyncNow: ManualSyncNow,
    recordLastSyncAttempt: RecordLastSyncAttempt,
    loadRemoteSyncSettings: LoadRemoteSyncSettings,
    saveRemoteSyncSettings: SaveRemoteSyncSettings,
    updateSyncToken: UpdateSyncToken,
    clearRemoteSyncSettings: ClearRemoteSyncSettings,
    testRemoteConnection: TestRemoteConnection,
    reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch,
    loadVaultSettings: LoadVaultSettings,
    saveVaultSettings: SaveVaultSettings,
    loadLocalSettings: LoadLocalSettings,
    saveLocalSettings: SaveLocalSettings,
    ensureDeviceInstanceId: EnsureDeviceInstanceId,
    searchVault: SearchVault,
    maintainVaultSearchIndex: MaintainVaultSearchIndex,
    repairVaultSearchIndex: RepairVaultSearchIndex,
    touchVaultSearchPaths: TouchVaultSearchPaths,
    loadPodcastCatalog: LoadPodcastCatalog,
    markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    podcastPlaylistWiring: PodcastPlaylistWiring,
    podcastPlayerDriver: PodcastPlayerDriver,
    syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
    onLaunchSettled: () -> Unit = {}
) {
    EskerraGoTheme(darkTheme = true) {
        CompositionLocalProvider(LocalParsedMarkdownCache provides parsedMarkdownCache) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val gateViewModel: AppGateViewModel = viewModel(
                    factory = AppGateViewModel.factory(
                        workspaceStore = workspaceStore,
                        bootCacheStore = bootCacheStore,
                        filesDir = filesDir
                    )
                )
                val gateState by gateViewModel.gateState.collectAsState()
                var inboxUiState by remember { mutableStateOf<InboxUiState?>(null) }
                var todayHubUiState by remember { mutableStateOf<TodayHubUiState?>(null) }

                AppLaunchSettledEffect(
                    gateState = gateState,
                    inboxUiState = inboxUiState,
                    todayHubUiState = todayHubUiState,
                    onLaunchSettled = onLaunchSettled
                )

                when (val gate = gateState) {
                    AppGateState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    is AppGateState.NeedsSetup -> {
                        SideEffect {
                            inboxUiState = null
                            todayHubUiState = null
                        }
                        val activity = LocalContext.current as? ComponentActivity
                        BackHandler {
                            activity?.finish()
                        }

                        val setupViewModel: WorkspaceSetupViewModel = viewModel(
                            factory = WorkspaceSetupViewModel.factory(
                                setupCompletion = setupCompletion,
                                filesDir = filesDir,
                                recoveryMessage = gate.recoveryMessage
                            )
                        )
                        val uiState = setupViewModel.uiState

                        WorkspaceSetupScreen(
                            state = uiState,
                            onNameChange = setupViewModel::onNameChange,
                            onBranchChange = setupViewModel::onBranchChange,
                            onRemoteUriChange = setupViewModel::onRemoteUriChange,
                            onCredentialChange = setupViewModel::onCredentialChange,
                            onModeChange = setupViewModel::onModeChange,
                            onSubmit = {
                                setupViewModel.submit { config ->
                                    gateViewModel.markReady(config)
                                }
                            }
                        )
                    }

                    is AppGateState.Ready -> App(
                        config = gate.config,
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
                        loadPodcastCatalog = loadPodcastCatalog,
                        markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
                        podcastPlaylistWiring = podcastPlaylistWiring,
                        podcastPlayerDriver = podcastPlayerDriver,
                        syncPodcastVaultRefresh = syncPodcastVaultRefresh,
                        onConfigUpdated = gateViewModel::updateReadyConfig,
                        onInboxUiStateChanged = { inboxUiState = it },
                        onTodayHubUiStateChanged = { todayHubUiState = it }
                    )
                }
            }
        }
    }
}
