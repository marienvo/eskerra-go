package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadEditableNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.core.usecase.SaveNote
import com.eskerra.go.feature.dashboard.DashboardScreen
import com.eskerra.go.feature.editor.CreateInboxScreen
import com.eskerra.go.feature.editor.NoteEditorScreen
import com.eskerra.go.feature.editor.NoteEditorUiState
import com.eskerra.go.feature.inbox.InboxScreen
import com.eskerra.go.feature.menu.MenuScreen
import com.eskerra.go.feature.note.NoteScreen
import com.eskerra.go.feature.podcasts.PodcastItem
import com.eskerra.go.feature.podcasts.PodcastsScreen
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
    loadGitStatusSummary: LoadGitStatusSummary
) {
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
                    factory = InboxViewModel.factory(
                        config = config,
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
                        config = config,
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
                    workspaceName = config.name,
                    noteCount = PLACEHOLDER_NOTE_COUNT,
                    gitStatus = PLACEHOLDER_GIT_STATUS
                )
            }

            composable(AppRoute.MENU) {
                MenuScreen(
                    items = fakeMenuItems,
                    onItemClick = { }
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
                        config = config,
                        filesDir = filesDir,
                        noteId = noteId,
                        loadNoteForReading = loadNoteForReading
                    )
                )
                val readerState by noteReaderViewModel.uiState.collectAsState()

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
                        config = config,
                        filesDir = filesDir,
                        noteId = noteId,
                        loadEditableNote = loadEditableNote,
                        saveNote = saveNote,
                        loadGitStatusSummary = loadGitStatusSummary
                    )
                )
                val editorState by editorViewModel.uiState.collectAsState()

                LaunchedEffect(editorState) {
                    val content = editorState as? NoteEditorUiState.Content
                    if (content?.saveMessage == NoteEditorViewModel.SAVED_MESSAGE) {
                        markInboxNotesChanged()
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

private const val NOTES_CHANGED_KEY = "notesChanged"

private fun NavHostController.markInboxNotesChanged() {
    runCatching {
        getBackStackEntry(AppRoute.INBOX).savedStateHandle[NOTES_CHANGED_KEY] = true
    }
}

private const val PLACEHOLDER_NOTE_COUNT = 0
private const val PLACEHOLDER_GIT_STATUS = "Placeholder — not connected"

private val fakePodcasts: List<PodcastItem> = listOf(
    PodcastItem(title = "Note-taking, deeply", author = "Eskerra FM"),
    PodcastItem(title = "Plain text forever", author = "Markdown Weekly"),
    PodcastItem(title = "Compose in practice", author = "Android Cafe")
)

private val fakeMenuItems: List<String> = listOf(
    "Settings",
    "Workspaces",
    "About"
)
