package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.model.hasSyncWork
import com.eskerra.go.core.repository.ActiveTodayHubStore
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
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.editor.CreateInboxScreen
import com.eskerra.go.feature.editor.NoteEditorScreen
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.menu.MenuScreen
import com.eskerra.go.feature.note.NoteReaderUiState
import com.eskerra.go.feature.note.NoteScreen
import com.eskerra.go.feature.podcasts.PodcastsScreen
import com.eskerra.go.feature.settings.VaultSettingsScreen
import com.eskerra.go.feature.sync.SyncScreen
import com.eskerra.go.feature.sync.SyncSettingsScreen
import com.eskerra.go.feature.sync.SyncUiState
import com.eskerra.go.ui.markdown.AmbiguousWikiLinkSheet
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
    onConfigUpdated: (WorkspaceConfig) -> Unit,
    onInboxUiStateChanged: (InboxUiState) -> Unit = {}
) {
    var currentConfig by remember(config) { mutableStateOf(config) }
    val workspaceRoot = remember(currentConfig, filesDir) {
        WorkspacePaths.resolve(filesDir, currentConfig.relativePath).getOrNull()
    }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val markInboxNotesChanged = {
        navController.markInboxNotesChanged()
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

    DisposableEffect(appSyncViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                appSyncViewModel.refreshShellStatusQuietly(forceRemote = false)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }

    val syncIndicator = shellSyncIndicatorState(syncState, remoteConfigured)

    AppShell(
        currentRoute = currentRoute,
        syncIndicator = syncIndicator,
        onSyncClick = {
            when (val state = syncState) {
                is SyncUiState.Ready -> when {
                    !state.status.hasSyncWork -> Unit
                    state.preflight.canSync -> appSyncViewModel.syncNow()
                    else -> navController.navigate(AppRoute.SYNC) {
                        launchSingleTop = true
                    }
                }
                SyncUiState.Loading,
                is SyncUiState.Syncing,
                is SyncUiState.Success -> Unit
                is SyncUiState.Error -> navController.navigate(AppRoute.SYNC) {
                    launchSingleTop = true
                }
            }
        },
        onNavigate = { route ->
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.INBOX,
            modifier = contentModifier
        ) {
            composable(AppRoute.INBOX) { entry ->
                AppInboxRoute(
                    currentConfig = currentConfig,
                    filesDir = filesDir,
                    loadInboxSummaries = loadInboxSummaries,
                    deleteInboxNotes = deleteInboxNotes,
                    loadTodayHub = loadTodayHub,
                    loadTodayHubRow = loadTodayHubRow,
                    activeTodayHubStore = activeTodayHubStore,
                    workspaceRoot = workspaceRoot,
                    currentRoute = currentRoute,
                    entry = entry,
                    navController = navController,
                    appSyncViewModel = appSyncViewModel,
                    touchVaultSearchPaths = touchVaultSearchPaths,
                    onInboxUiStateChanged = onInboxUiStateChanged
                )
            }

            composable(AppRoute.CREATE_INBOX) {
                val createViewModel: CreateInboxNoteViewModel = viewModel(
                    factory = CreateInboxNoteViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        createInboxNote = createInboxNote
                    )
                )
                val createState by createViewModel.uiState.collectAsState()

                LaunchedEffect(createViewModel) {
                    createViewModel.savedNoteId.collect { noteId ->
                        if (noteId != null) {
                            markInboxNotesChanged()
                            appSyncViewModel.refreshLocalStatusQuietly()
                            scope.touchVaultSearchPathsAsync(
                                touchVaultSearchPaths,
                                currentConfig,
                                filesDir,
                                listOf(noteId.value)
                            )
                            navController.navigate(AppRoute.note(noteId)) {
                                popUpTo(AppRoute.CREATE_INBOX) { inclusive = true }
                            }
                        }
                    }
                }

                CreateInboxScreen(
                    state = createState,
                    onBack = { navController.popBackStack() },
                    onDraftChange = createViewModel::updateDraft,
                    onSave = createViewModel::save
                )
            }

            composable(AppRoute.SEARCH) {
                AppSearchRoute(
                    currentConfig = currentConfig,
                    filesDir = filesDir,
                    searchVault = searchVault,
                    maintainVaultSearchIndex = maintainVaultSearchIndex,
                    repairVaultSearchIndex = repairVaultSearchIndex,
                    navController = navController
                )
            }

            composable(AppRoute.PODCASTS) {
                PodcastsScreen(podcasts = fakePodcasts)
            }

            composable(AppRoute.MENU) {
                MenuScreen(
                    items = menuItems,
                    onItemClick = { item ->
                        when (item) {
                            MENU_SEARCH -> navController.navigate(AppRoute.SEARCH)
                            MENU_SYNC -> navController.navigate(AppRoute.SYNC)
                            MENU_SETTINGS -> navController.navigate(AppRoute.SETTINGS)
                        }
                    }
                )
            }

            composable(AppRoute.SYNC) {
                SyncScreen(
                    state = syncState,
                    onSyncNow = appSyncViewModel::syncNow,
                    onRetry = appSyncViewModel::refreshLocalStatus,
                    onOpenSettings = { navController.navigate(AppRoute.SYNC_SETTINGS) }
                )
            }

            composable(AppRoute.SETTINGS) {
                val vaultSettingsViewModel: VaultSettingsViewModel = viewModel(
                    factory = VaultSettingsViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        loadVaultSettings = loadVaultSettings,
                        saveVaultSettings = saveVaultSettings,
                        loadLocalSettings = loadLocalSettings,
                        saveLocalSettings = saveLocalSettings,
                        ensureDeviceInstanceId = ensureDeviceInstanceId
                    )
                )
                val vaultSettingsState by vaultSettingsViewModel.uiState.collectAsState()
                VaultSettingsScreen(
                    state = vaultSettingsState,
                    onR2EndpointChange = vaultSettingsViewModel::onR2EndpointChange,
                    onR2JurisdictionChange = vaultSettingsViewModel::onR2JurisdictionChange,
                    onR2BucketChange = vaultSettingsViewModel::onR2BucketChange,
                    onR2AccessKeyIdChange = vaultSettingsViewModel::onR2AccessKeyIdChange,
                    onR2SecretAccessKeyChange = vaultSettingsViewModel::onR2SecretAccessKeyChange,
                    onDisplayNameChange = vaultSettingsViewModel::onDisplayNameChange,
                    onDeviceNameChange = vaultSettingsViewModel::onDeviceNameChange,
                    onSave = vaultSettingsViewModel::save
                )
            }

            composable(AppRoute.SYNC_SETTINGS) {
                val settingsViewModel: SyncSettingsViewModel = viewModel(
                    key = currentConfig.syncViewModelKey(),
                    factory = SyncSettingsViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        loadRemoteSyncSettings = loadRemoteSyncSettings,
                        saveRemoteSyncSettings = saveRemoteSyncSettings,
                        clearRemoteSyncSettings = clearRemoteSyncSettings,
                        testRemoteConnection = testRemoteConnection,
                        onConfigUpdated = { updated ->
                            currentConfig = updated
                            onConfigUpdated(updated)
                        }
                    )
                )
                val settingsState by settingsViewModel.uiState.collectAsState()

                SyncSettingsScreen(
                    state = settingsState,
                    onRemoteUriChange = settingsViewModel::onRemoteUriChange,
                    onBranchChange = settingsViewModel::onBranchChange,
                    onReplacementTokenChange = settingsViewModel::onReplacementTokenChange,
                    onSave = settingsViewModel::saveSettings,
                    onTestConnection = settingsViewModel::testConnection,
                    onClear = settingsViewModel::clearSettings
                )
            }

            composable(
                route = AppRoute.NOTE_PATTERN,
                arguments = listOf(
                    navArgument(AppRoute.NOTE_ARG) { type = NavType.StringType }
                )
            ) { entry ->
                val raw = entry.arguments?.getString(AppRoute.NOTE_ARG).orEmpty()
                val noteId = AppRoute.decodeNoteId(raw)
                val noteReaderViewModel: NoteReaderViewModel = viewModel(
                    factory = NoteReaderViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        noteId = noteId,
                        loadNoteForReading = loadNoteForReading,
                        prefetchLinkedNotes = prefetchLinkedNotes
                    )
                )
                val readerState by noteReaderViewModel.uiState.collectAsState()

                LaunchedEffect(currentRoute) {
                    if (consumeNoteReaderChanged(currentRoute, noteId, entry.savedStateHandle)) {
                        noteReaderViewModel.retry()
                    }
                }

                val noteReaderContext = LocalContext.current
                var ambiguousCandidates by remember { mutableStateOf<List<NoteId>?>(null) }

                NoteScreen(
                    state = readerState,
                    onRetry = noteReaderViewModel::retry,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(AppRoute.editor(noteId)) },
                    onOpenInternalNote = { targetId: NoteId ->
                        navController.navigate(AppRoute.note(targetId))
                    },
                    onOpenExternalUrl = { url: String ->
                        openExternalUrl(noteReaderContext, url)
                    },
                    onAmbiguousWikiLink = { candidates: List<NoteId>, _: String ->
                        ambiguousCandidates = candidates
                    },
                    onNoteNotFound = { message: String ->
                        showNoteNotFoundToast(noteReaderContext, message)
                    },
                    workspaceRoot = workspaceRoot
                )

                val registry = (readerState as? NoteReaderUiState.Content)?.document?.registry
                if (ambiguousCandidates != null && registry != null) {
                    AmbiguousWikiLinkSheet(
                        candidates = ambiguousCandidates!!,
                        registry = registry,
                        onPickNote = { picked ->
                            ambiguousCandidates = null
                            navController.navigate(AppRoute.note(picked))
                        },
                        onDismiss = { ambiguousCandidates = null }
                    )
                }
            }

            composable(
                route = AppRoute.EDITOR_PATTERN,
                arguments = listOf(
                    navArgument(AppRoute.EDITOR_ARG) { type = NavType.StringType }
                )
            ) { entry ->
                val raw = entry.arguments?.getString(AppRoute.EDITOR_ARG).orEmpty()
                val noteId = AppRoute.decodeEditorNoteId(raw)
                val editorViewModel: NoteEditorViewModel = viewModel(
                    factory = NoteEditorViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        noteId = noteId,
                        loadEditableNote = loadEditableNote,
                        saveNote = saveNote,
                        loadGitStatusSummary = loadGitStatusSummary
                    )
                )
                val editorState by editorViewModel.uiState.collectAsState()

                LaunchedEffect(editorViewModel) {
                    editorViewModel.noteSavedEvents.collect {
                        markInboxNotesChanged()
                        appSyncViewModel.refreshLocalStatusQuietly()
                        scope.touchVaultSearchPathsAsync(
                            touchVaultSearchPaths,
                            currentConfig,
                            filesDir,
                            listOf(noteId.value)
                        )
                        navController.markNoteReaderChanged(noteId)
                        navController.navigate(AppRoute.note(noteId)) {
                            popUpTo(AppRoute.editor(noteId)) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                NoteEditorScreen(
                    state = editorState,
                    onBack = { navController.popBackStack() },
                    onDraftChange = editorViewModel::updateDraft,
                    onSave = editorViewModel::save,
                    onRetry = editorViewModel::retry
                )
            }
        }
    }
}

/** ViewModel key for sync screens; includes branch so branch-only updates recreate VMs. */
private fun WorkspaceConfig.syncViewModelKey(): String = "${remoteUri.orEmpty()}:$branch"
