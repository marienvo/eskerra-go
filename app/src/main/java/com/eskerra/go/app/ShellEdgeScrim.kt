package com.eskerra.go.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Extra fade below the status bar inset for the top edge scrim. */
internal val ShellTopScrimExtra = 32.dp

/** Extra fade above the navigation bar inset for the bottom edge scrim. */
internal val ShellBottomScrimExtra = 48.dp

private const val SCRIM_MID_ALPHA = 0.6f

internal fun topEdgeScrimColors(background: Color): List<Color> = listOf(
    background,
    background.copy(alpha = SCRIM_MID_ALPHA),
    Color.Transparent
)

internal fun bottomEdgeScrimColors(background: Color): List<Color> = listOf(
    Color.Transparent,
    background.copy(alpha = SCRIM_MID_ALPHA),
    background
)

/** Subtle top vignette so scrolling content fades before the status bar. */
@Composable
fun ShellTopEdgeScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTop + ShellTopScrimExtra)
            .background(Brush.verticalGradient(colors = topEdgeScrimColors(background)))
    )
}

/** Subtle bottom vignette so scrolling content fades above the floating taskbar. */
@Composable
fun ShellBottomEdgeScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navigationBarBottom + ShellBottomScrimExtra)
            .background(Brush.verticalGradient(colors = bottomEdgeScrimColors(background)))
    )
}
