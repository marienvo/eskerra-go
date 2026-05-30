package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.core.usecase.UpdateSyncToken
import com.eskerra.go.feature.dashboard.DashboardScreen
import com.eskerra.go.feature.editor.CreateInboxScreen
import com.eskerra.go.feature.editor.NoteEditorScreen
import com.eskerra.go.feature.inbox.InboxScreen
import com.eskerra.go.feature.menu.MenuScreen
import com.eskerra.go.feature.note.NoteScreen
import com.eskerra.go.feature.podcasts.PodcastItem
import com.eskerra.go.feature.podcasts.PodcastsScreen
import com.eskerra.go.feature.sync.SyncScreen
import com.eskerra.go.feature.sync.SyncSettingsScreen
import java.io.File

/**
 * Root composable. Owns the navigation graph and wires ViewModels to stateless
 * feature screens.
 */
@Composable
fun App(
    config: WorkspaceConfig,
    filesDir: File,
    loadInboxSummaries: LoadInboxSummaries,
    loadNoteForReading: LoadNoteForReading,
    createInboxNote: CreateInboxNote,
    loadEditableNote: LoadEditableNote,
    saveNote: SaveNote,
    loadGitStatusSummary: LoadGitStatusSummary,
    loadSyncStatus: LoadSyncStatus,
    manualSyncNow: ManualSyncNow,
    loadRemoteSyncSettings: LoadRemoteSyncSettings,
    saveRemoteSyncSettings: SaveRemoteSyncSettings,
    updateSyncToken: UpdateSyncToken,
    clearRemoteSyncSettings: ClearRemoteSyncSettings,
    testRemoteConnection: TestRemoteConnection,
    onConfigUpdated: (WorkspaceConfig) -> Unit
) {
    var currentConfig by remember(config) { mutableStateOf(config) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val markInboxNotesChanged = {
        navController.markInboxNotesChanged()
    }

    AppShell(
        currentRoute = currentRoute,
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
                val inboxViewModel: InboxViewModel = viewModel(
                    key = currentConfig.remoteUri,
                    factory = InboxViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        loadInboxSummaries = loadInboxSummaries
                    )
                )
                val inboxState by inboxViewModel.uiState.collectAsState()

                LaunchedEffect(currentRoute) {
                    val notesChanged = entry.savedStateHandle.remove<Boolean>(
                        NOTES_CHANGED_KEY
                    ) == true
                    if (currentRoute == AppRoute.INBOX && notesChanged) {
                        inboxViewModel.refresh()
                    }
                }

                InboxScreen(
                    state = inboxState,
                    onRetry = inboxViewModel::refresh,
                    onNoteClick = { noteId ->
                        navController.navigate(AppRoute.note(noteId))
                    }
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
                    createViewModel.createdNoteId.collect { noteId ->
                        if (noteId != null) {
                            markInboxNotesChanged()
                            navController.navigate(AppRoute.editor(noteId)) {
                                popUpTo(AppRoute.CREATE_INBOX) { inclusive = true }
                            }
                        }
                    }
                }

                CreateInboxScreen(
                    state = createState,
                    onRetry = createViewModel::retry
                )
            }

            composable(AppRoute.PODCASTS) {
                PodcastsScreen(podcasts = fakePodcasts)
            }

            composable(AppRoute.DASHBOARD) {
                DashboardScreen(
                    workspaceName = currentConfig.name,
                    noteCount = PLACEHOLDER_NOTE_COUNT,
                    gitStatus = PLACEHOLDER_GIT_STATUS
                )
            }

            composable(AppRoute.MENU) {
                MenuScreen(
                    items = menuItems,
                    onItemClick = { item ->
                        when (item) {
                            MENU_SYNC -> navController.navigate(AppRoute.SYNC)
                            MENU_SETTINGS -> navController.navigate(AppRoute.SYNC_SETTINGS)
                        }
                    }
                )
            }

            composable(AppRoute.SYNC) {
                val syncViewModel: SyncViewModel = viewModel(
                    key = currentConfig.syncViewModelKey(),
                    factory = SyncViewModel.factory(
                        config = currentConfig,
                        filesDir = filesDir,
                        loadSyncStatus = loadSyncStatus,
                        manualSyncNow = manualSyncNow,
                        onSyncSuccess = markInboxNotesChanged,
                        onConfigUpdated = { updated ->
                            currentConfig = updated
                            onConfigUpdated(updated)
                        }
                    )
                )
                val syncState by syncViewModel.uiState.collectAsState()

                SyncScreen(
                    state = syncState,
                    onSyncNow = syncViewModel::syncNow,
                    onRetry = syncViewModel::refreshStatus,
                    onOpenSettings = { navController.navigate(AppRoute.SYNC_SETTINGS) }
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
                        loadNoteForReading = loadNoteForReading
                    )
                )
                val readerState by noteReaderViewModel.uiState.collectAsState()

                LaunchedEffect(currentRoute) {
                    if (consumeNoteReaderChanged(currentRoute, noteId, entry.savedStateHandle)) {
                        noteReaderViewModel.retry()
                    }
                }

                NoteScreen(
                    state = readerState,
                    onRetry = noteReaderViewModel::retry,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(AppRoute.editor(noteId)) },
                    onResolvedWikiLinkClick = { targetId: NoteId ->
                        navController.navigate(AppRoute.note(targetId))
                    }
                )
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
                        navController.markNoteReaderChanged(noteId)
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

private const val NOTES_CHANGED_KEY = "notesChanged"
internal const val NOTE_CONTENT_CHANGED_KEY = "noteContentChanged"

internal fun consumeNoteReaderChanged(
    currentRoute: String?,
    noteId: NoteId,
    savedStateHandle: SavedStateHandle
): Boolean {
    if (currentRoute != AppRoute.note(noteId)) return false
    return savedStateHandle.remove<Boolean>(NOTE_CONTENT_CHANGED_KEY) == true
}

private fun NavHostController.markInboxNotesChanged() {
    runCatching {
        getBackStackEntry(AppRoute.INBOX).savedStateHandle[NOTES_CHANGED_KEY] = true
    }
}

private fun NavHostController.markNoteReaderChanged(noteId: NoteId) {
    runCatching {
        getBackStackEntry(AppRoute.note(noteId))
            .savedStateHandle[NOTE_CONTENT_CHANGED_KEY] = true
    }
}

private const val PLACEHOLDER_NOTE_COUNT = 0
private const val PLACEHOLDER_GIT_STATUS = "Placeholder — not connected"

private val fakePodcasts: List<PodcastItem> = listOf(
    PodcastItem(title = "Note-taking, deeply", author = "Eskerra FM"),
    PodcastItem(title = "Plain text forever", author = "Markdown Weekly"),
    PodcastItem(title = "Compose in practice", author = "Android Cafe")
)

private const val MENU_SYNC = "Sync"
private const val MENU_SETTINGS = "Settings"
private const val MENU_WORKSPACES = "Workspaces"
private const val MENU_ABOUT = "About"

private val menuItems: List<String> = listOf(
    MENU_SYNC,
    MENU_SETTINGS,
    MENU_WORKSPACES,
    MENU_ABOUT
)
