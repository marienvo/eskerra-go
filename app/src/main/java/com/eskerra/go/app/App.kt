package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.data.git.FakeGitGateway
import com.eskerra.go.data.workspace.FakeWorkspace
import com.eskerra.go.feature.add.AddScreen
import com.eskerra.go.feature.dashboard.DashboardScreen
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
    loadNoteForReading: LoadNoteForReading
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
            composable(AppRoute.INBOX) {
                val inboxViewModel: InboxViewModel = viewModel(
                    factory = InboxViewModel.factory(
                        config = config,
                        filesDir = filesDir,
                        loadInboxSummaries = loadInboxSummaries
                    )
                )
                val inboxState by inboxViewModel.uiState.collectAsState()

                InboxScreen(
                    state = inboxState,
                    onRetry = inboxViewModel::refresh,
                    onNoteClick = { noteId ->
                        navController.navigate(AppRoute.note(noteId))
                    }
                )
            }

            composable(AppRoute.ADD) {
                var title by rememberSaveable { mutableStateOf("") }
                var body by rememberSaveable { mutableStateOf("") }
                AddScreen(
                    title = title,
                    body = body,
                    onTitleChange = { title = it },
                    onBodyChange = { body = it },
                    // Intentionally not persisted in this UI-only step.
                    onSave = { _, _ -> }
                )
            }

            composable(AppRoute.PODCASTS) {
                PodcastsScreen(podcasts = fakePodcasts)
            }

            composable(AppRoute.DASHBOARD) {
                val workspace = FakeWorkspace.current
                DashboardScreen(
                    workspaceName = workspace.name,
                    noteCount = workspace.noteCount,
                    gitStatus = FakeGitGateway.status
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
                    onResolvedWikiLinkClick = { targetId: NoteId ->
                        navController.navigate(AppRoute.note(targetId))
                    }
                )
            }
        }
    }
}

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
