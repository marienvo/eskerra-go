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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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

/**
 * Vertical offset (in px, `<= 0`) to apply to the top scrim so that [revealedPx] of its
 * [fullPx]-tall height is on screen, with the rest translated up above the top edge. Clamps
 * [revealedPx] to `[0, fullPx]` so callers can pass raw, unclamped scroll offsets.
 */
internal fun topScrimTranslationPx(revealedPx: Float, fullPx: Float): Float =
    revealedPx.coerceIn(0f, fullPx) - fullPx

/**
 * Tracks how far the floating top scrim should be revealed, driven by scroll position rather
 * than time. A screen registers a reveal provider via [ShellTopScrimReveal] that reports the
 * distance (in px) its own scroll has moved from rest; the scrim shows the *smallest* distance
 * reported by any currently composed screen, so it stays hidden while any registered screen is
 * still at its own top. A screen that never registers leaves the scrim fully revealed, as before.
 */
internal class ShellTopScrimController {
    private var revealProviders by mutableStateOf(emptyMap<Any, () -> Float>())

    /** The revealed distance (px), clamped to `[0, fullPx]`, given no registered screen limits it. */
    fun revealedPx(fullPx: Float): Float {
        val providers = revealProviders
        if (providers.isEmpty()) return fullPx
        var min = Float.MAX_VALUE
        for (provider in providers.values) {
            val value = provider()
            if (value < min) min = value
        }
        return min.coerceIn(0f, fullPx)
    }

    fun register(key: Any, revealedPx: () -> Float) {
        revealProviders = revealProviders + (key to revealedPx)
    }

    fun clear(key: Any) {
        revealProviders = revealProviders - key
    }
}

internal val LocalShellTopScrim = compositionLocalOf { ShellTopScrimController() }

/**
 * Registers [revealedPx] as this screen's contribution to how far the floating top scrim should
 * be revealed: the distance, in px, the screen's own scroll has moved from rest (0 at rest, at
 * least the scrim's full height once scrolled past it). The scrim tracks the scroll 1:1, so it
 * slides into place at exactly the speed the user scrolls rather than fading in on a timer.
 * [revealedPx] is read during drawing, not composition, so scrolling doesn't trigger recomposition.
 * Unregisters automatically when this composable leaves composition, so a screen that never calls
 * this leaves the scrim in its default, fully revealed state.
 */
@Composable
fun ShellTopScrimReveal(revealedPx: () -> Float) {
    val controller = LocalShellTopScrim.current
    val key = remember { Any() }
    DisposableEffect(Unit) {
        onDispose { controller.clear(key) }
    }
    SideEffect {
        controller.register(key, revealedPx)
    }
}

/** Draws edge scrims over content without intercepting touch events. */
@Composable
fun Modifier.shellEdgeScrimOverlay(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topHeight = statusBarTop + ShellTopScrimExtra
    val bottomHeight = navigationBarBottom + ShellBottomScrimExtra
    val topScrimController = LocalShellTopScrim.current
    return drawWithContent {
        drawContent()
        val topHeightPx = topHeight.toPx()
        val revealedPx = topScrimController.revealedPx(topHeightPx)
        translate(top = topScrimTranslationPx(revealedPx, topHeightPx)) {
            drawTopEdgeScrim(background, topHeight)
        }
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
