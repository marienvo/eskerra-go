package com.eskerra.go.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eskerra.go.ui.theme.EskerraChromeTokens

/**
 * Floating navigation shell. It overlays controls on top of the current screen:
 * - top and bottom edge scrims so content fades under the floating chrome
 * - top-left Notes and Podcasts tab buttons (icon + label)
 * - a bottom new-note input while reading the vault
 * - a top-right hamburger Menu button carrying the sync count/attention badge
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
    newNoteInputVisible: Boolean = false,
    newNoteDraft: String = "",
    newNoteCanSave: Boolean = false,
    newNoteIsSaving: Boolean = false,
    newNoteErrorMessage: String? = null,
    onNewNoteDraftChange: (String) -> Unit = {},
    onNewNoteSave: () -> Unit = {},
    onNewNoteSearch: (String) -> Unit = {},
    onMenuClick: () -> Unit,
    onNavigate: (route: String) -> Unit,
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    val chromeInsets = rememberShellChromeInsets(
        miniPlayerVisible = miniPlayerVisible,
        newNoteInputVisible = newNoteInputVisible
    )

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
                    .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelTabButton(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = "Notes",
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
                    .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgedBox(
                    badge = {
                        val badgeText = syncIndicator?.badgeText
                        if (badgeText != null) {
                            Badge { Text(badgeText) }
                        }
                    }
                ) {
                    ShellChromeButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            }

            if (newNoteInputVisible) {
                ShellNewNoteInput(
                    draft = newNoteDraft,
                    canSave = newNoteCanSave,
                    isSaving = newNoteIsSaving,
                    errorMessage = newNoteErrorMessage,
                    onDraftChange = onNewNoteDraftChange,
                    onSave = onNewNoteSave,
                    onSearch = onNewNoteSearch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 11.dp)
                )
            }

            if (miniPlayer != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
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
    ShellChromeButton(onClick = onClick, horizontalContentPadding = 14.dp) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(20.dp)
        )
        Text(text = label, color = tint)
    }
}
