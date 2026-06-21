package com.eskerra.go.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellChromeInsetsTest {
    @Test
    fun calculateShellChromeInsets_withoutBottomChrome_usesSystemBarsOnly() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 24.dp,
            navigationBarBottom = 48.dp
        )

        assertEquals(ShellTopChromeHeight + 24.dp, insets.top)
        assertEquals(48.dp, insets.bottom)
    }

    @Test
    fun calculateShellChromeInsets_withZeroSystemBars_hasTopChromeOnly() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp
        )

        assertEquals(ShellTopChromeHeight, insets.top)
        assertEquals(0.dp, insets.bottom)
    }

    @Test
    fun calculateShellChromeInsets_withNewNoteInput_addsInputHeight() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp,
            newNoteInputVisible = true
        )

        assertEquals(ShellTopChromeHeight, insets.top)
        assertEquals(ShellNewNoteInputHeight, insets.bottom)
    }

    @Test
    fun calculateShellChromeInsets_withMiniPlayer_addsMiniPlayerHeight() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp,
            miniPlayerVisible = true
        )

        assertEquals(ShellTopChromeHeight, insets.top)
        assertEquals(ShellMiniPlayerHeight, insets.bottom)
    }

    @Test
    fun calculateShellChromeInsets_withBothBottomModes_prefersMiniPlayer() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp,
            miniPlayerVisible = true,
            newNoteInputVisible = true
        )

        assertEquals(ShellTopChromeHeight, insets.top)
        assertEquals(ShellMiniPlayerHeight, insets.bottom)
    }
}
