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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
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

/** Draws edge scrims over content without intercepting touch events. */
@Composable
fun Modifier.shellEdgeScrimOverlay(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topHeight = statusBarTop + ShellTopScrimExtra
    val bottomHeight = navigationBarBottom + ShellBottomScrimExtra
    return drawWithContent {
        drawContent()
        drawTopEdgeScrim(background, topHeight)
        drawBottomEdgeScrim(background, bottomHeight)
    }
}

internal fun DrawScope.drawTopEdgeScrim(background: Color, height: Dp) {
    val heightPx = height.toPx()
    if (heightPx <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(
            colors = topEdgeScrimColors(background),
            startY = 0f,
            endY = heightPx
        ),
        size = Size(size.width, heightPx)
    )
}

internal fun DrawScope.drawBottomEdgeScrim(background: Color, height: Dp) {
    val heightPx = height.toPx()
    if (heightPx <= 0f) return
    val topY = size.height - heightPx
    drawRect(
        brush = Brush.verticalGradient(
            colors = bottomEdgeScrimColors(background),
            startY = topY,
            endY = size.height
        ),
        topLeft = Offset(0f, topY),
        size = Size(size.width, heightPx)
    )
}

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
