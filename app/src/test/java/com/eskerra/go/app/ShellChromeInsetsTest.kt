package com.eskerra.go.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellChromeInsetsTest {
    @Test
    fun calculateShellChromeInsets_usesFixedChromeHeightsPlusSystemBars() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 24.dp,
            navigationBarBottom = 48.dp
        )

        assertEquals(ShellTopChromeHeight + 24.dp, insets.top)
        assertEquals(ShellBottomChromeHeight + 48.dp, insets.bottom)
    }

    @Test
    fun calculateShellChromeInsets_withZeroSystemBars_matchesChromeConstants() {
        val insets = calculateShellChromeInsets(
            statusBarTop = 0.dp,
            navigationBarBottom = 0.dp
        )

        assertEquals(ShellTopChromeHeight, insets.top)
        assertEquals(ShellBottomChromeHeight, insets.bottom)
    }
}
