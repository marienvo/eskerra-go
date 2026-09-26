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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.eskerra.go.data.perf.ColdStartTrace
import com.eskerra.go.feature.sync.SyncSpinner

/**
 * Floating navigation shell. It overlays controls on top of the current screen:
 * - top and bottom edge scrims so content fades under the floating chrome
 * - a bottom new-note input while reading the vault
 * - a top-right hamburger Menu button carrying the sync count/attention badge
 *
 * The shell owns no app state. It reports the menu overlay through [onMenuClick], and renders the
 * active screen edge-to-edge via [content]. Scrollable screens
 * should apply [LocalShellChromeInsets] through [shellScrollContentPadding] so content can pass under
 * the floating chrome while remaining reachable.
 */
@Composable
fun AppShell(
    syncIndicator: ShellSyncIndicatorState?,
    pullToRefreshActive: Boolean = false,
    shellInput: ShellInputPresentation? = null,
    onMenuClick: () -> Unit,
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    val chromeInsets = rememberShellChromeInsets(
        newNoteInputVisible = shellInput?.visible == true
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
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgedBox(
                    badge = {
                        val badgeText = syncIndicator?.badgeText
                        if (syncIndicator?.spinning == true) {
                            if (!pullToRefreshActive) {
                                Badge { SyncSpinner(modifier = Modifier.size(8.dp)) }
                            }
                        } else if (badgeText != null) {
                            Badge { Text(badgeText) }
                        }
                    }
                ) {
                    ShellChromeButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            }

            if (shellInput?.visible == true) {
                ShellNewNoteInput(
                    searchMode = shellInput.searchMode,
                    onSearchModeChange = shellInput.onSearchModeChange,
                    value = shellInput.value,
                    onValueChange = shellInput.onValueChange,
                    onSubmit = shellInput.onSubmit,
                    submitEnabled = shellInput.submitEnabled,
                    isSaving = shellInput.isSaving,
                    errorMessage = shellInput.errorMessage,
                    fieldSignal = shellInput.fieldSignal,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .onGloballyPositioned { ColdStartTrace.markInputReady() }
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 11.dp)
                )
            }

        }
    }
}
