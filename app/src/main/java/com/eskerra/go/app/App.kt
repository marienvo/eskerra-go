package com.eskerra.go.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.repository.VaultSyncScheduler
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
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.NotePrefetchScheduler
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.ReportWeeklyPerformance
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.SnoozeInboxNotes
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.core.usecase.WarmNote
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.note.LocalNoteWorkspaceHeader
import com.eskerra.go.feature.note.NoteWorkspaceHeader
import com.eskerra.go.feature.sync.AppSyncViewModel
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.feature.todayhub.TodayHubViewModel
import java.io.File

/** Root composable. Owns the navigation graph and wires ViewModels to stateless feature screens. */
@Composable
fun App(
    config: WorkspaceConfig,
    filesDir: File,
    launchSettled: Boolean,
    loadInboxSummaries: LoadInboxSummariesCached,
    loadNoteForReading: LoadNoteForReading,
    notePrefetchScheduler: NotePrefetchScheduler,
    warmNote: WarmNote,
    createInboxNote: CreateInboxNote,
    deleteInboxNotes: DeleteInboxNotes,
    snoozeInboxNotes: SnoozeInboxNotes,
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
    syncStateRepository: SyncStateRepository,
    vaultSyncScheduler: VaultSyncScheduler,
    loadRemoteSyncSettings: LoadRemoteSyncSettings,
    saveRemoteSyncSettings: SaveRemoteSyncSettings,
    updateSyncToken: UpdateSyncToken,
    clearRemoteSyncSettings: ClearRemoteSyncSettings,
    testRemoteConnection: TestRemoteConnection,
    reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch,
    reportWeeklyPerformance: ReportWeeklyPerformance,
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
    shareIntake: ShareIntake,
    notificationOpenRequest: NotificationOpenRequest,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    readConfig: suspend () -> WorkspaceConfig? = { null },
    onConfigUpdated: (WorkspaceConfig) -> Unit,
    onInboxUiStateChanged: (InboxUiState) -> Unit = {},
    onTodayHubUiStateChanged: (TodayHubUiState) -> Unit = {}
) {
    var currentConfig by remember(config) { mutableStateOf(config) }
    val workspaceRoot = remember(currentConfig, filesDir) {
        WorkspacePaths.resolve(filesDir, currentConfig.relativePath).getOrNull()
    }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val noteOpenGate = remember { NoteOpenGate() }
    val datedHeaderRetention = remember { DatedNoteHeaderRetention() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var homeReselectSignal by remember { mutableIntStateOf(0) }
    var hubPickerVisible by remember { mutableStateOf(false) }
    var inboxRefreshSignal by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val markInboxNotesChanged: () -> Unit = {
        navController.markInboxNotesChanged()
        inboxRefreshSignal++
    }
    val appSyncViewModel: AppSyncViewModel = viewModel(
        key = currentConfig.syncViewModelKey(),
        factory = AppSyncViewModel.factory(
            config = currentConfig,
            loadSyncStatus = { cfg -> loadSyncStatus(cfg, filesDir) },
            refreshRemoteSyncStatus = { cfg -> refreshRemoteSyncStatus(cfg, filesDir) },
            buildSyncPreflight = { cfg -> buildSyncPreflight(cfg, filesDir) },
            buildSafeSyncDiagnostic = { cfg -> buildSafeSyncDiagnostic(cfg, filesDir) },
            syncStateRepository = syncStateRepository,
            vaultSyncScheduler = vaultSyncScheduler,
            readConfig = readConfig,
            onSyncSuccess = markInboxNotesChanged,
            onConfigUpdated = {
                currentConfig = it
                onConfigUpdated(it)
            }
        )
    )
    val syncState by appSyncViewModel.uiState.collectAsState()
    val remoteConfigured = !currentConfig.remoteUri.isNullOrBlank()
    val todayHubViewModel: TodayHubViewModel = viewModel(
        key = todayHubViewModelKey(currentConfig.remoteUri),
        factory = TodayHubViewModel.factory(
            config = currentConfig,
            filesDir = filesDir,
            loadTodayHub = loadTodayHub,
            loadTodayHubRow = loadTodayHubRow,
            activeTodayHubStore = activeTodayHubStore,
            todayHubSnapshotStore = todayHubSnapshotStore
        )
    )
    val todayHubState by todayHubViewModel.uiState.collectAsState()
    LaunchedEffect(todayHubState) {
        onTodayHubUiStateChanged(todayHubState)
    }
    AppTodayHubEffects(todayHubViewModel, launchSettled)
    AppBootEffects(
        config = currentConfig,
        filesDir = filesDir,
        launchSettled = launchSettled,
        todayHubUiState = todayHubState,
        notePrefetchScheduler = notePrefetchScheduler,
        reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch,
        appSyncViewModel = appSyncViewModel,
        reportWeeklyPerformance = reportWeeklyPerformance,
        onConfigUpdated = onConfigUpdated,
        onConfigChanged = { currentConfig = it }
    )
    AppSearchIndexEffects(
        config = currentConfig,
        filesDir = filesDir,
        launchSettled = launchSettled,
        maintainVaultSearchIndex = maintainVaultSearchIndex
    )
    AppForegroundSyncEffect(appSyncViewModel)

    val syncIndicator = rememberShellSyncIndicator(appSyncViewModel, remoteConfigured)
    val shellInputState = rememberShellInput(
        currentConfig = currentConfig,
        filesDir = filesDir,
        createInboxNote = createInboxNote,
        activeTodayHubStore = activeTodayHubStore,
        touchVaultSearchPaths = touchVaultSearchPaths,
        appSyncViewModel = appSyncViewModel,
        scope = scope,
        currentRoute = currentRoute,
        markInboxNotesChanged = markInboxNotesChanged,
        searchVault = searchVault,
        maintainVaultSearchIndex = maintainVaultSearchIndex,
        repairVaultSearchIndex = repairVaultSearchIndex,
        navController = navController,
        shareIntake = shareIntake
    )
    val onInbox = currentRoute == AppRoute.INBOX || currentRoute == null
    val pullRefreshing by appSyncViewModel.pullRefreshing.collectAsState()
    val onSelectHub: (NoteId) -> Unit = { selected ->
        val activeHubId = (todayHubState as? TodayHubUiState.Content)?.activeHubId
        if (selected != activeHubId) {
            todayHubViewModel.selectHub(selected)
        }
        if (onInbox) {
            if (selected == activeHubId) homeReselectSignal++
        } else {
            navController.popBackStack(AppRoute.INBOX, inclusive = false)
        }
    }
    AppShell(
        syncIndicator = syncIndicator,
        pullToRefreshActive = onInbox && pullRefreshing,
        shellInput = shellInputState.presentation,
        onMenuClick = { menuOpen = true },
        onBack = if (AppRoute.isNoteReaderRoute(currentRoute)) {
            { navController.popBackStack() }
        } else {
            null
        },
        onWorkspaceSwitch = if (onInbox && todayHubState.canSwitchHub()) {
            { hubPickerVisible = true }
        } else {
            null
        }
    ) { contentModifier ->
        val navGraphContext = AppNavGraphContext(
            currentConfig = currentConfig,
            filesDir = filesDir,
            workspaceRoot = workspaceRoot,
            currentRoute = currentRoute,
            navController = navController,
            scope = scope,
            noteOpenGate = noteOpenGate,
            datedHeaderRetention = datedHeaderRetention,
            appSyncViewModel = appSyncViewModel,
            syncState = syncState,
            homeReselectSignal = homeReselectSignal,
            onSelectHub = onSelectHub,
            inboxRefreshSignal = inboxRefreshSignal,
            launchSettled = launchSettled,
            todayHubViewModel = todayHubViewModel,
            todayHubUiState = todayHubState,
            loadInboxSummaries = loadInboxSummaries,
            loadNoteForReading = loadNoteForReading,
            notePrefetchScheduler = notePrefetchScheduler,
            warmNote = warmNote,
            deleteInboxNotes = deleteInboxNotes,
            snoozeInboxNotes = snoozeInboxNotes,
            loadEditableNote = loadEditableNote,
            saveNote = saveNote,
            loadGitStatusSummary = loadGitStatusSummary,
            loadTodayHub = loadTodayHub,
            activeTodayHubStore = activeTodayHubStore,
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
            notificationOpenRequest = notificationOpenRequest,
            notificationsEnabled = notificationsEnabled,
            onNotificationsEnabledChange = onNotificationsEnabledChange,
            markInboxNotesChanged = markInboxNotesChanged,
            onConfigUpdated = { updated ->
                currentConfig = updated
                onConfigUpdated(updated)
            },
            onInboxUiStateChanged = onInboxUiStateChanged
        )
        CompositionLocalProvider(
            LocalNoteWorkspaceHeader provides NoteWorkspaceHeader(
                state = todayHubState,
                onSelectHub = onSelectHub
            )
        ) {
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
                sharedDestinations(navGraphContext)
            }
        }
    }

    LaunchedEffect(notificationOpenRequest) {
        val ids = notificationOpenRequest.noteIds
        if (ids.size == 1) {
            navController.navigate(AppRoute.notificationNote(NoteId(ids.single())))
        } else if (ids.isNotEmpty()) {
            navController.navigate(AppRoute.NOTIFICATION_LIST)
        }
    }

    AppHubPicker(
        visible = hubPickerVisible,
        state = todayHubState,
        onSelectHub = onSelectHub,
        onDismiss = { hubPickerVisible = false }
    )

    if (menuOpen) {
        AppMenuSheet(
            items = buildMenuEntries(),
            onDismiss = { menuOpen = false },
            onItemClick = { id ->
                when (id) {
                    MENU_SYNC_STATUS -> navController.navigate(AppRoute.SYNC)
                    MENU_SYNC_SETTINGS -> navController.navigate(AppRoute.SYNC_SETTINGS)
                }
            }
        )
    }
}
