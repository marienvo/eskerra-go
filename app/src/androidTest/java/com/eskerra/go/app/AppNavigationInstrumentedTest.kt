package com.eskerra.go.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun searchNote_fromPodcasts_staysInPodcastsStack() {
        setTestContent()

        composeRule.onNodeWithContentDescription("Podcasts").performClick()
        assertCurrentRoute(AppRoute.PODCASTS)

        composeRule.runOnIdle {
            navController.navigate(AppRoute.SEARCH)
            navController.navigate(AppRoute.note(NoteId("Inbox/Test.md")))
        }
        assertCurrentRoute(AppRoute.NOTE_PATTERN)

        composeRule.onNodeWithContentDescription("Notes").performClick()
        assertCurrentRoute(AppRoute.INBOX)

        composeRule.onNodeWithContentDescription("Podcasts").performClick()
        assertCurrentRoute(AppRoute.NOTE_PATTERN)
    }

    private fun setTestContent() {
        composeRule.setContent {
            MaterialTheme {
                TestNavigationShell()
            }
        }
    }

    @Composable
    private fun TestNavigationShell() {
        val controller = rememberNavController()
        navController = controller
        val backStackEntry by controller.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val destinationTopLevelRoute = topLevelGraphRouteForDestination(currentDestination)
        var currentTopLevelRoute by remember { mutableStateOf(AppRoute.HOME_GRAPH) }

        LaunchedEffect(destinationTopLevelRoute) {
            if (destinationTopLevelRoute != null) {
                currentTopLevelRoute = destinationTopLevelRoute
            }
        }

        val activeTopLevelRoute = destinationTopLevelRoute ?: currentTopLevelRoute
        AppShell(
            selectedTopLevelRoute = activeTopLevelRoute,
            syncIndicator = null,
            onSyncClick = {},
            onMenuClick = {},
            onNavigate = { route ->
                controller.navigateTab(
                    currentRoute = currentDestination?.route,
                    targetRoute = route,
                    currentTopLevelRoute = activeTopLevelRoute,
                    onHomeReselected = {}
                )
            }
        ) { modifier ->
            NavHost(
                navController = controller,
                startDestination = AppRoute.HOME_GRAPH,
                modifier = modifier
            ) {
                navigation(startDestination = AppRoute.INBOX, route = AppRoute.HOME_GRAPH) {
                    composable(AppRoute.INBOX) {}
                }
                navigation(
                    startDestination = AppRoute.PODCASTS,
                    route = AppRoute.PODCASTS_GRAPH
                ) {
                    composable(AppRoute.PODCASTS) {}
                }
                composable(AppRoute.SEARCH) {}
                composable(
                    route = AppRoute.NOTE_PATTERN,
                    arguments = listOf(
                        navArgument(AppRoute.NOTE_ARG) { type = NavType.StringType }
                    )
                ) {}
            }
        }
    }

    private fun assertCurrentRoute(expectedRoute: String) {
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(expectedRoute, navController.currentDestination?.route)
        }
    }
}
