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
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.FakeGitGateway
import com.eskerra.go.data.notes.FakeNotes
import com.eskerra.go.data.notes.LoadInboxSummaries
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
 * Root composable. Owns the navigation graph and is the only layer that reads
 * fake data. It reads from `data/` packages and passes plain state and callbacks into
 * the stateless feature screens, which never touch the data layer themselves.
 */
@Composable
fun App(config: WorkspaceConfig, filesDir: File, loadInboxSummaries: LoadInboxSummaries) {
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
                    onRetry = inboxViewModel::refresh
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
                val note = FakeNotes.note(AppRoute.decodeNoteId(raw))
                NoteScreen(
                    title = note?.summary?.title ?: "Note not found",
                    body = note?.body ?: "This note does not exist in the fake data.",
                    onWikiLinkClick = { target ->
                        FakeNotes.resolveWikiLink(target)?.let { targetId ->
                            navController.navigate(AppRoute.note(targetId))
                        }
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
