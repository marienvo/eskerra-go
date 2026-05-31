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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Floating navigation shell. It overlays controls on top of the current screen:
 * - a bottom floating taskbar with Inbox, a large centered Add, and Podcasts
 * - a top-right sync button (when remote is configured) and hamburger Menu button
 *
 * The shell owns no app state. It reports navigation intents through [onNavigate]
 * and renders the active screen edge-to-edge via [content]. Scrollable screens
 * should apply [LocalShellChromeInsets] through [shellScrollContentPadding] so
 * content can pass under the floating chrome while remaining reachable.
 */
@Composable
fun AppShell(
    currentRoute: String?,
    syncIndicator: ShellSyncIndicatorState?,
    onSyncClick: () -> Unit,
    onNavigate: (route: String) -> Unit,
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    val chromeInsets = rememberShellChromeInsets()

    CompositionLocalProvider(LocalShellChromeInsets provides chromeInsets) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize())

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
                SmallFloatingActionButton(onClick = { onNavigate(AppRoute.MENU) }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                }
            }

            BottomTaskbar(
                currentRoute = currentRoute,
                onInbox = { onNavigate(AppRoute.INBOX) },
                onAdd = { onNavigate(AppRoute.CREATE_INBOX) },
                onPodcasts = { onNavigate(AppRoute.PODCASTS) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun BottomTaskbar(
    currentRoute: String?,
    onInbox: () -> Unit,
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
                icon = Icons.Filled.Inbox,
                contentDescription = "Inbox",
                selected = currentRoute == AppRoute.INBOX,
                onClick = onInbox
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
                selected = currentRoute == AppRoute.PODCASTS,
                onClick = onPodcasts
            )
        }
    }
}

@Composable
private fun TaskbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
