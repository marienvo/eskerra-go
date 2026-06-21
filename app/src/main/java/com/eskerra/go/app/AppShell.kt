package com.eskerra.go.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eskerra.go.ui.theme.EskerraChromeTokens

/**
 * Floating navigation shell. It overlays controls on top of the current screen:
 * - top and bottom edge scrims so content fades under the status bar and taskbar
 * - top-left Home and Podcasts tab buttons (icon + label)
 * - a bottom floating taskbar with Home, a large centered Add, and Podcasts
 * - a top-right sync button (when remote is configured) and hamburger Menu button
 *
 * The shell owns no app state. It reports navigation intents through [onNavigate], the menu overlay
 * through [onMenuClick], and renders the active screen edge-to-edge via [content]. Scrollable screens
 * should apply [LocalShellChromeInsets] through [shellScrollContentPadding] so content can pass under
 * the floating chrome while remaining reachable.
 */
@Composable
fun AppShell(
    selectedTopLevelRoute: String?,
    syncIndicator: ShellSyncIndicatorState?,
    miniPlayerVisible: Boolean = false,
    miniPlayer: (@Composable () -> Unit)? = null,
    onSyncClick: () -> Unit,
    onMenuClick: () -> Unit,
    onNavigate: (route: String) -> Unit,
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    val chromeInsets = rememberShellChromeInsets(miniPlayerVisible)

    CompositionLocalProvider(LocalShellChromeInsets provides chromeInsets) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(
                Modifier
                    .fillMaxSize()
                    .shellEdgeScrimOverlay()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelTabButton(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    selected = selectedTopLevelRoute == AppRoute.HOME_GRAPH,
                    onClick = { onNavigate(AppRoute.HOME_GRAPH) }
                )
                TopLevelTabButton(
                    icon = Icons.Filled.Podcasts,
                    label = "Podcasts",
                    selected = selectedTopLevelRoute == AppRoute.PODCASTS_GRAPH,
                    onClick = { onNavigate(AppRoute.PODCASTS_GRAPH) }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (syncIndicator != null) {
                    ShellSyncButton(
                        state = syncIndicator,
                        onClick = onSyncClick
                    )
                }
                SmallFloatingActionButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                }
            }

            BottomTaskbar(
                selectedTopLevelRoute = selectedTopLevelRoute,
                onHome = { onNavigate(AppRoute.HOME_GRAPH) },
                onAdd = { onNavigate(AppRoute.CREATE_INBOX) },
                onPodcasts = { onNavigate(AppRoute.PODCASTS_GRAPH) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )

            if (miniPlayer != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
                ) {
                    miniPlayer()
                }
            }
        }
    }
}

@Composable
private fun TopLevelTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) {
        EskerraChromeTokens.HeaderText
    } else {
        EskerraChromeTokens.HeaderInactive
    }
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp)
        )
        Text(text = label, color = tint)
    }
}

@Composable
private fun BottomTaskbar(
    selectedTopLevelRoute: String?,
    onHome: () -> Unit,
    onAdd: () -> Unit,
    onPodcasts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TaskbarButton(
                icon = Icons.Filled.Home,
                contentDescription = "Home",
                selected = selectedTopLevelRoute == AppRoute.HOME_GRAPH,
                onClick = onHome
            )
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
            TaskbarButton(
                icon = Icons.Filled.Podcasts,
                contentDescription = "Podcasts",
                selected = selectedTopLevelRoute == AppRoute.PODCASTS_GRAPH,
                onClick = onPodcasts
            )
        }
    }
}

@Composable
private fun TaskbarButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) {
        EskerraChromeTokens.HeaderText
    } else {
        EskerraChromeTokens.HeaderInactive
    }
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
