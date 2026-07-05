package com.eskerra.go.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadDownloadedBinaries
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadPodcastArtwork
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
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.todayhub.TodayHubUiState
import java.io.File

/**
 * Root composable. Owns the navigation graph and wires ViewModels to stateless
 * feature screens.
 */
@Composable
fun App(
    config: WorkspaceConfig,
    filesDir: File,
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
    syncBinaries: SyncBinaries,
    loadDownloadedBinaries: LoadDownloadedBinaries,
    searchVault: SearchVault,
    maintainVaultSearchIndex: MaintainVaultSearchIndex,
    repairVaultSearchIndex: RepairVaultSearchIndex,
    touchVaultSearchPaths: TouchVaultSearchPaths,
    loadPodcastCatalog: LoadPodcastCatalog,
    markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    podcastPlaylistWiring: PodcastPlaylistWiring,
    loadPodcastArtwork: LoadPodcastArtwork,
    podcastPlayerDriver: PodcastPlayerDriver,
    syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
    catalogSnapshotStore: PodcastCatalogSnapshotStore,
    podcastShellStateWiring: PodcastShellStateWiring,
    onConfigUpdated: (WorkspaceConfig) -> Unit,
    onInboxUiStateChanged: (InboxUiState) -> Unit = {},
    onTodayHubUiStateChanged: (TodayHubUiState) -> Unit = {},
    onPodcastFirstLaunchChanged: (Boolean) -> Unit = {}
) {
    var currentConfig by remember(config) { mutableStateOf(config) }
    val workspaceRoot = remember(currentConfig, filesDir) {
        WorkspacePaths.resolve(filesDir, currentConfig.relativePath).getOrNull()
    }
    val playlistPollingHost = rememberPlaylistR2PollingHost(
        workspaceRoot = workspaceRoot,
        loadVaultSettings = loadVaultSettings,
        playlistSyncRepository = podcastPlaylistWiring.repository,
        playlistR2ConditionalFetch = podcastPlaylistWiring.conditionalFetch,
        podcastPlayerDriver = podcastPlayerDriver
    )
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val destinationTopLevelRoute = topLevelGraphRouteForDestination(currentDestination)
    var currentTopLevelRoute by remember { mutableStateOf(AppRoute.HOME_GRAPH) }
    LaunchedEffect(destinationTopLevelRoute) {
        if (destinationTopLevelRoute != null) {
            currentTopLevelRoute = destinationTopLevelRoute
        }
    }
    // Bumped on each Home tap while already on the inbox; the inbox route reacts (it owns the Today
    // Hub state and decides whether to snap to the current week). A route change can't carry this
    // because re-tapping Home does not navigate.
    var homeReselectSignal by remember { mutableIntStateOf(0) }
    var inboxRefreshSignal by remember { mutableIntStateOf(0) }
    // The hamburger menu is an overlay, not a NavHost destination: opening it must not navigate, so
    // the underlying route (wherever you were) stays put and closing it cannot jump back to Home.
    var menuOpen by remember { mutableStateOf(false) }
    val markInboxNotesChanged: () -> Unit = {
        navController.markInboxNotesChanged()
        inboxRefreshSignal++
    }
    val appSyncViewModel: AppSyncViewModel = viewModel(
        key = currentConfig.syncViewModelKey(),
        factory = AppSyncViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadSyncStatus = loadSyncStatus,
            refreshRemoteSyncStatus = refreshRemoteSyncStatus,
            buildSyncPreflight = buildSyncPreflight,
            buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
            manualSyncNow = manualSyncNow,
            recordLastSyncAttempt = recordLastSyncAttempt,
            onSyncSuccess = markInboxNotesChanged,
            onConfigUpdated = { updated ->
                currentConfig = updated
                onConfigUpdated(updated)
            }
        )
    )
    val syncState by appSyncViewModel.uiState.collectAsState()
    val remoteConfigured = !currentConfig.remoteUri.isNullOrBlank()

    AppBootEffects(
        config = currentConfig,
        filesDir = filesDir,
        reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
        appSyncViewModel = appSyncViewModel,
        onConfigUpdated = onConfigUpdated,
        onConfigChanged = { updated -> currentConfig = updated }
    )
    AppSearchIndexEffects(
        config = currentConfig,
        filesDir = filesDir,
        maintainVaultSearchIndex = maintainVaultSearchIndex
    )
    AppForegroundSyncEffect(appSyncViewModel)

    val syncIndicator = shellSyncIndicatorState(syncState, remoteConfigured)
    val selectedTopLevelRoute = destinationTopLevelRoute ?: currentTopLevelRoute
    val inPodcastMode = selectedTopLevelRoute == AppRoute.PODCASTS_GRAPH
    val newNoteInputState = rememberShellNewNoteInputState(
        currentConfig = currentConfig,
        filesDir = filesDir,
        createInboxNote = createInboxNote,
        activeTodayHubStore = activeTodayHubStore,
        touchVaultSearchPaths = touchVaultSearchPaths,
        appSyncViewModel = appSyncViewModel,
        scope = scope,
        currentRoute = currentRoute,
        selectedTopLevelRoute = selectedTopLevelRoute,
        markInboxNotesChanged = markInboxNotesChanged
    )
    val shellInputState = rememberAppShellInputState(
        currentConfig = currentConfig,
        filesDir = filesDir,
        searchVault = searchVault,
        maintainVaultSearchIndex = maintainVaultSearchIndex,
        repairVaultSearchIndex = repairVaultSearchIndex,
        navController = navController,
        currentRoute = currentRoute,
        newNoteInputState = newNoteInputState
    )
    val podcastShellBridge = remember { PodcastShellBridge() }
    val miniPlayerMount = rememberAppShellMiniPlayerMount(
        currentConfig = currentConfig,
        filesDir = filesDir,
        loadPodcastArtwork = loadPodcastArtwork,
        markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
        podcastPlayerDriver = podcastPlayerDriver,
        bridge = podcastShellBridge,
        inPodcastMode = inPodcastMode
    )
    AppPodcastBootstrap(
        currentConfig = currentConfig,
        filesDir = filesDir,
        workspaceRoot = workspaceRoot,
        currentRoute = currentRoute,
        navController = navController,
        podcastPlayerDriver = podcastPlayerDriver,
        podcastShellStateWiring = podcastShellStateWiring,
        podcastPlaylistSync = podcastPlaylistWiring.sync,
        loadPodcastArtwork = loadPodcastArtwork,
        playlistPollingHost = playlistPollingHost,
        bridge = podcastShellBridge,
        currentDestination = currentDestination,
        onPodcastFirstLaunchChanged = onPodcastFirstLaunchChanged
    )
    AppShell(
        selectedTopLevelRoute = selectedTopLevelRoute,
        syncIndicator = syncIndicator,
        miniPlayerVisible = miniPlayerMount.visible,
        miniPlayer = miniPlayerMount.content,
        shellInput = shellInputState.presentation,
        onMenuClick = { menuOpen = true },
        onNavigate = { route ->
            navController.navigateTab(
                currentRoute = currentRoute,
                currentTopLevelRoute = destinationTopLevelRoute ?: currentTopLevelRoute,
                targetRoute = route
            ) {
                homeReselectSignal++
            }
        }
    ) { contentModifier ->
        val navGraphContext = AppNavGraphContext(
            currentConfig = currentConfig,
            filesDir = filesDir,
            workspaceRoot = workspaceRoot,
            currentRoute = currentRoute,
            navController = navController,
            scope = scope,
            appSyncViewModel = appSyncViewModel,
            syncState = syncState,
            homeReselectSignal = homeReselectSignal,
            inboxRefreshSignal = inboxRefreshSignal,
            loadInboxSummaries = loadInboxSummaries,
            loadNoteForReading = loadNoteForReading,
            prefetchLinkedNotes = prefetchLinkedNotes,
            deleteInboxNotes = deleteInboxNotes,
            loadEditableNote = loadEditableNote,
            saveNote = saveNote,
            loadGitStatusSummary = loadGitStatusSummary,
            loadTodayHub = loadTodayHub,
            loadTodayHubRow = loadTodayHubRow,
            activeTodayHubStore = activeTodayHubStore,
            todayHubSnapshotStore = todayHubSnapshotStore,
            loadRemoteSyncSettings = loadRemoteSyncSettings,
            saveRemoteSyncSettings = saveRemoteSyncSettings,
            clearRemoteSyncSettings = clearRemoteSyncSettings,
            testRemoteConnection = testRemoteConnection,
            loadVaultSettings = loadVaultSettings,
            saveVaultSettings = saveVaultSettings,
            loadLocalSettings = loadLocalSettings,
            saveLocalSettings = saveLocalSettings,
            ensureDeviceInstanceId = ensureDeviceInstanceId,
            syncBinaries = syncBinaries,
            loadDownloadedBinaries = loadDownloadedBinaries,
            searchVault = searchVault,
            maintainVaultSearchIndex = maintainVaultSearchIndex,
            repairVaultSearchIndex = repairVaultSearchIndex,
            searchViewModel = shellInputState.searchViewModel,
            touchVaultSearchPaths = touchVaultSearchPaths,
            loadPodcastCatalog = loadPodcastCatalog,
            markPodcastEpisodesPlayed = markPodcastEpisodesPlayed,
            podcastPlaylistSync = podcastPlaylistWiring.sync,
            loadPodcastArtwork = loadPodcastArtwork,
            podcastPlayerDriver = podcastPlayerDriver,
            syncPodcastVaultRefresh = syncPodcastVaultRefresh,
            catalogSnapshotStore = catalogSnapshotStore,
            persistPodcastPlaybackSnapshot = podcastShellStateWiring.persistPodcastPlaybackSnapshot,
            clearPodcastPlaybackSnapshot = podcastShellStateWiring.clearPodcastPlaybackSnapshot,
            podcastShellBridge = podcastShellBridge,
            playlistPollingHost = playlistPollingHost,
            markInboxNotesChanged = markInboxNotesChanged,
            onConfigUpdated = { updated ->
                currentConfig = updated
                onConfigUpdated(updated)
            },
            onInboxUiStateChanged = onInboxUiStateChanged,
            onTodayHubUiStateChanged = onTodayHubUiStateChanged
        )
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME_GRAPH,
            modifier = contentModifier,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            homeGraph(navGraphContext)
            podcastsGraph(navGraphContext)
            sharedDestinations(navGraphContext)
        }
    }

    if (menuOpen) {
        AppMenuSheet(
            items = buildMenuEntries(syncIndicator?.changeCount, remoteConfigured),
            onDismiss = { menuOpen = false },
            onItemClick = { id ->
                when (id) {
                    MENU_SYNC_NOW -> onMenuSyncClick(syncState, appSyncViewModel, navController)
                    MENU_SYNC_SETTINGS -> navController.navigate(AppRoute.SYNC)
                    MENU_SETTINGS -> navController.navigate(AppRoute.SYNC_SETTINGS)
                }
            }
        )
    }
}
