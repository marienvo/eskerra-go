package com.eskerra.go.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.DeleteInboxNotes
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummariesCached
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.core.usecase.PrefetchLinkedNotes
import com.eskerra.go.core.usecase.RepairVaultSearchIndex
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.core.usecase.SearchVault
import com.eskerra.go.core.usecase.SyncPodcastVaultRefresh
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import com.eskerra.go.feature.editor.CreateInboxScreen
import com.eskerra.go.feature.editor.NoteEditorScreen
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.note.NoteReaderUiState
import com.eskerra.go.feature.note.NoteScreen
import com.eskerra.go.feature.podcasts.PlaylistR2PollingHost
import com.eskerra.go.feature.settings.VaultSettingsScreen
import com.eskerra.go.feature.sync.SyncScreen
import com.eskerra.go.feature.sync.SyncSettingsScreen
import com.eskerra.go.feature.sync.SyncUiState
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.AmbiguousWikiLinkSheet
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Wiring passed from [App] into the NavHost route builders. */
internal data class AppNavGraphContext(
    val currentConfig: WorkspaceConfig,
    val filesDir: File,
    val workspaceRoot: File?,
    val currentRoute: String?,
    val navController: NavHostController,
    val scope: CoroutineScope,
    val appSyncViewModel: AppSyncViewModel,
    val syncState: SyncUiState,
    val homeReselectSignal: Int,
    val loadInboxSummaries: LoadInboxSummariesCached,
    val loadNoteForReading: LoadNoteForReading,
    val prefetchLinkedNotes: PrefetchLinkedNotes,
    val createInboxNote: CreateInboxNote,
    val deleteInboxNotes: DeleteInboxNotes,
    val loadEditableNote: LoadEditableNote,
    val saveNote: SaveNote,
    val loadGitStatusSummary: LoadGitStatusSummary,
    val loadTodayHub: LoadTodayHub,
    val loadTodayHubRow: LoadTodayHubRow,
    val activeTodayHubStore: ActiveTodayHubStore,
    val todayHubSnapshotStore: TodayHubSnapshotStore,
    val loadRemoteSyncSettings: LoadRemoteSyncSettings,
    val saveRemoteSyncSettings: SaveRemoteSyncSettings,
    val clearRemoteSyncSettings: ClearRemoteSyncSettings,
    val testRemoteConnection: TestRemoteConnection,
    val loadVaultSettings: LoadVaultSettings,
    val saveVaultSettings: SaveVaultSettings,
    val loadLocalSettings: LoadLocalSettings,
    val saveLocalSettings: SaveLocalSettings,
    val ensureDeviceInstanceId: EnsureDeviceInstanceId,
    val searchVault: SearchVault,
    val maintainVaultSearchIndex: MaintainVaultSearchIndex,
    val repairVaultSearchIndex: RepairVaultSearchIndex,
    val touchVaultSearchPaths: TouchVaultSearchPaths,
    val loadPodcastCatalog: LoadPodcastCatalog,
    val markPodcastEpisodesPlayed: MarkPodcastEpisodesPlayed,
    val podcastPlaylistSync: PodcastPlaylistSync,
    val loadPodcastArtwork: LoadPodcastArtwork,
    val podcastPlayerDriver: PodcastPlayerDriver,
    val syncPodcastVaultRefresh: SyncPodcastVaultRefresh,
    val catalogSnapshotStore: PodcastCatalogSnapshotStore,
    val persistPodcastPlaybackSnapshot: PersistPodcastPlaybackSnapshot,
    val clearPodcastPlaybackSnapshot: ClearPodcastPlaybackSnapshot,
    val podcastShellBridge: PodcastShellBridge,
    val playlistPollingHost: PlaylistR2PollingHost?,
    val markInboxNotesChanged: () -> Unit,
    val onConfigUpdated: (WorkspaceConfig) -> Unit,
    val onInboxUiStateChanged: (InboxUiState) -> Unit,
    val onTodayHubUiStateChanged: (TodayHubUiState) -> Unit
)

internal fun NavGraphBuilder.homeGraph(ctx: AppNavGraphContext) {
    navigation(startDestination = AppRoute.INBOX, route = AppRoute.HOME_GRAPH) {
        opaqueComposable(AppRoute.INBOX) { entry ->
            AppInboxRoute(
                currentConfig = ctx.currentConfig,
                filesDir = ctx.filesDir,
                loadInboxSummaries = ctx.loadInboxSummaries,
                deleteInboxNotes = ctx.deleteInboxNotes,
                loadTodayHub = ctx.loadTodayHub,
                loadTodayHubRow = ctx.loadTodayHubRow,
                activeTodayHubStore = ctx.activeTodayHubStore,
                todayHubSnapshotStore = ctx.todayHubSnapshotStore,
                workspaceRoot = ctx.workspaceRoot,
                currentRoute = ctx.currentRoute,
                entry = entry,
                navController = ctx.navController,
                appSyncViewModel = ctx.appSyncViewModel,
                touchVaultSearchPaths = ctx.touchVaultSearchPaths,
                onInboxUiStateChanged = ctx.onInboxUiStateChanged,
                onTodayHubUiStateChanged = ctx.onTodayHubUiStateChanged,
                homeReselectSignal = ctx.homeReselectSignal
            )
        }
    }
}

internal fun NavGraphBuilder.podcastsGraph(ctx: AppNavGraphContext) {
    navigation(startDestination = AppRoute.PODCASTS, route = AppRoute.PODCASTS_GRAPH) {
        opaqueComposable(AppRoute.PODCASTS) {
            AppPodcastsRoute(
                currentConfig = ctx.currentConfig,
                filesDir = ctx.filesDir,
                loadPodcastCatalog = ctx.loadPodcastCatalog,
                markPodcastEpisodesPlayed = ctx.markPodcastEpisodesPlayed,
                podcastPlaylistSync = ctx.podcastPlaylistSync,
                loadPodcastArtwork = ctx.loadPodcastArtwork,
                podcastPlayerDriver = ctx.podcastPlayerDriver,
                syncPodcastVaultRefresh = ctx.syncPodcastVaultRefresh,
                catalogSnapshotStore = ctx.catalogSnapshotStore,
                persistPodcastPlaybackSnapshot = ctx.persistPodcastPlaybackSnapshot,
                clearPodcastPlaybackSnapshot = ctx.clearPodcastPlaybackSnapshot,
                loadLocalSettings = ctx.loadLocalSettings,
                podcastShellBridge = ctx.podcastShellBridge,
                playlistPollingHost = ctx.playlistPollingHost
            )
        }
    }
}

internal fun NavGraphBuilder.sharedDestinations(ctx: AppNavGraphContext) {
    opaqueComposable(AppRoute.CREATE_INBOX) {
        val createViewModel: CreateInboxNoteViewModel = viewModel(
            factory = CreateInboxNoteViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                createInboxNote = ctx.createInboxNote
            )
        )
        val createState by createViewModel.uiState.collectAsState()

        LaunchedEffect(createViewModel) {
            createViewModel.savedNoteId.collect { noteId ->
                if (noteId != null) {
                    ctx.markInboxNotesChanged()
                    ctx.appSyncViewModel.refreshLocalStatusQuietly()
                    ctx.scope.touchVaultSearchPathsAsync(
                        ctx.touchVaultSearchPaths,
                        ctx.currentConfig,
                        ctx.filesDir,
                        listOf(noteId.value)
                    )
                    ctx.navController.navigate(AppRoute.note(noteId)) {
                        popUpTo(AppRoute.CREATE_INBOX) { inclusive = true }
                    }
                }
            }
        }

        CreateInboxScreen(
            state = createState,
            onBack = { ctx.navController.popBackStack() },
            onDraftChange = createViewModel::updateDraft,
            onSave = createViewModel::save
        )
    }
    opaqueComposable(AppRoute.SEARCH) {
        AppSearchRoute(
            currentConfig = ctx.currentConfig,
            filesDir = ctx.filesDir,
            searchVault = ctx.searchVault,
            maintainVaultSearchIndex = ctx.maintainVaultSearchIndex,
            repairVaultSearchIndex = ctx.repairVaultSearchIndex,
            navController = ctx.navController
        )
    }

    opaqueComposable(AppRoute.SYNC) {
        SyncScreen(
            state = ctx.syncState,
            onSyncNow = ctx.appSyncViewModel::syncNow,
            onRetry = ctx.appSyncViewModel::refreshLocalStatus,
            onOpenSettings = { ctx.navController.navigate(AppRoute.SYNC_SETTINGS) }
        )
    }

    opaqueComposable(AppRoute.SETTINGS) {
        val vaultSettingsViewModel: VaultSettingsViewModel = viewModel(
            factory = VaultSettingsViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                loadVaultSettings = ctx.loadVaultSettings,
                saveVaultSettings = ctx.saveVaultSettings,
                loadLocalSettings = ctx.loadLocalSettings,
                saveLocalSettings = ctx.saveLocalSettings,
                ensureDeviceInstanceId = ctx.ensureDeviceInstanceId
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

    opaqueComposable(AppRoute.SYNC_SETTINGS) {
        val settingsViewModel: SyncSettingsViewModel = viewModel(
            key = ctx.currentConfig.syncViewModelKey(),
            factory = SyncSettingsViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                loadRemoteSyncSettings = ctx.loadRemoteSyncSettings,
                saveRemoteSyncSettings = ctx.saveRemoteSyncSettings,
                clearRemoteSyncSettings = ctx.clearRemoteSyncSettings,
                testRemoteConnection = ctx.testRemoteConnection,
                onConfigUpdated = { updated ->
                    ctx.onConfigUpdated(updated)
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

    opaqueComposable(
        route = AppRoute.NOTE_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.NOTE_ARG) { type = NavType.StringType }
        )
    ) { entry ->
        val raw = entry.arguments?.getString(AppRoute.NOTE_ARG).orEmpty()
        val noteId = AppRoute.decodeNoteId(raw)
        val noteReaderViewModel: NoteReaderViewModel = viewModel(
            factory = NoteReaderViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                noteId = noteId,
                loadNoteForReading = ctx.loadNoteForReading,
                prefetchLinkedNotes = ctx.prefetchLinkedNotes
            )
        )
        val readerState by noteReaderViewModel.uiState.collectAsState()

        LaunchedEffect(ctx.currentRoute) {
            if (consumeNoteReaderChanged(ctx.currentRoute, noteId, entry.savedStateHandle)) {
                noteReaderViewModel.retry()
            }
        }

        val noteReaderContext = LocalContext.current
        var ambiguousCandidates by remember { mutableStateOf<List<NoteId>?>(null) }

        NoteScreen(
            state = readerState,
            onRetry = noteReaderViewModel::retry,
            onBack = { ctx.navController.popBackStack() },
            onEdit = { ctx.navController.navigate(AppRoute.editor(noteId)) },
            onOpenInternalNote = { targetId: NoteId ->
                ctx.navController.navigate(AppRoute.note(targetId))
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
            workspaceRoot = ctx.workspaceRoot
        )

        val registry = (readerState as? NoteReaderUiState.Content)?.document?.registry
        if (ambiguousCandidates != null && registry != null) {
            AmbiguousWikiLinkSheet(
                candidates = ambiguousCandidates!!,
                registry = registry,
                onPickNote = { picked ->
                    ambiguousCandidates = null
                    ctx.navController.navigate(AppRoute.note(picked))
                },
                onDismiss = { ambiguousCandidates = null }
            )
        }
    }

    opaqueComposable(
        route = AppRoute.EDITOR_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.EDITOR_ARG) { type = NavType.StringType }
        )
    ) { entry ->
        val raw = entry.arguments?.getString(AppRoute.EDITOR_ARG).orEmpty()
        val noteId = AppRoute.decodeEditorNoteId(raw)
        val editorViewModel: NoteEditorViewModel = viewModel(
            factory = NoteEditorViewModel.factory(
                config = ctx.currentConfig,
                filesDir = ctx.filesDir,
                noteId = noteId,
                loadEditableNote = ctx.loadEditableNote,
                saveNote = ctx.saveNote,
                loadGitStatusSummary = ctx.loadGitStatusSummary
            )
        )
        val editorState by editorViewModel.uiState.collectAsState()

        LaunchedEffect(editorViewModel) {
            editorViewModel.noteSavedEvents.collect {
                ctx.markInboxNotesChanged()
                ctx.appSyncViewModel.refreshLocalStatusQuietly()
                ctx.scope.touchVaultSearchPathsAsync(
                    ctx.touchVaultSearchPaths,
                    ctx.currentConfig,
                    ctx.filesDir,
                    listOf(noteId.value)
                )
                ctx.navController.markNoteReaderChanged(noteId)
                ctx.navController.navigate(AppRoute.note(noteId)) {
                    popUpTo(AppRoute.editor(noteId)) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        NoteEditorScreen(
            state = editorState,
            onBack = { ctx.navController.popBackStack() },
            onDraftChange = editorViewModel::updateDraft,
            onSave = editorViewModel::save,
            onRetry = editorViewModel::retry
        )
    }
}

/** ViewModel key for sync screens; includes branch so branch-only updates recreate VMs. */
internal fun WorkspaceConfig.syncViewModelKey(): String = "${remoteUri.orEmpty()}:$branch"
