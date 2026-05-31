package com.eskerra.go.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vertical space reserved for floating shell chrome (sync, menu, taskbar). */
internal val ShellTopChromeHeight = 56.dp
internal val ShellBottomChromeHeight = 104.dp

private val ShellHorizontalContentPadding = 16.dp

data class ShellChromeInsets(val top: Dp, val bottom: Dp) {
    fun asPaddingValues(): PaddingValues = PaddingValues(top = top, bottom = bottom)

    companion object {
        val Zero = ShellChromeInsets(top = 0.dp, bottom = 0.dp)
    }
}

fun calculateShellChromeInsets(statusBarTop: Dp, navigationBarBottom: Dp) = ShellChromeInsets(
    top = ShellTopChromeHeight + statusBarTop,
    bottom = ShellBottomChromeHeight + navigationBarBottom
)

@Composable
fun rememberShellChromeInsets(): ShellChromeInsets {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return calculateShellChromeInsets(statusBarTop, navigationBarBottom)
}

val LocalShellChromeInsets = compositionLocalOf { ShellChromeInsets.Zero }

@Composable
fun shellScrollContentPadding(): PaddingValues {
    val chrome = LocalShellChromeInsets.current
    return PaddingValues(
        start = ShellHorizontalContentPadding,
        top = chrome.top,
        end = ShellHorizontalContentPadding,
        bottom = chrome.bottom
    )
}
