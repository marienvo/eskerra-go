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

/** Vertical space reserved for floating shell chrome. */
internal val ShellTopChromeHeight = 56.dp
internal val ShellNewNoteInputHeight = 88.dp
internal val ShellMiniPlayerHeight = 168.dp

private val ShellHorizontalContentPadding = 16.dp

data class ShellChromeInsets(val top: Dp, val bottom: Dp) {
    fun asPaddingValues(): PaddingValues = PaddingValues(top = top, bottom = bottom)

    companion object {
        val Zero = ShellChromeInsets(top = 0.dp, bottom = 0.dp)
    }
}

fun calculateShellChromeInsets(
    statusBarTop: Dp,
    navigationBarBottom: Dp,
    miniPlayerVisible: Boolean = false,
    newNoteInputVisible: Boolean = false
) = ShellChromeInsets(
    top = ShellTopChromeHeight + statusBarTop,
    bottom = navigationBarBottom +
        when {
            miniPlayerVisible -> ShellMiniPlayerHeight
            newNoteInputVisible -> ShellNewNoteInputHeight
            else -> 0.dp
        }
)

@Composable
fun rememberShellChromeInsets(
    miniPlayerVisible: Boolean = false,
    newNoteInputVisible: Boolean = false
): ShellChromeInsets {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return calculateShellChromeInsets(
        statusBarTop = statusBarTop,
        navigationBarBottom = navigationBarBottom,
        miniPlayerVisible = miniPlayerVisible,
        newNoteInputVisible = newNoteInputVisible
    )
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
