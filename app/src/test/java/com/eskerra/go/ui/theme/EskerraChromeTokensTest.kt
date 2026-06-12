package com.eskerra.go.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec §10 dark-mode chrome contract — token values must match the rebuild spec table. */
class EskerraChromeTokensTest {

    @Test
    fun headerText_isWhite() {
        assertEquals(Color(0xFFFFFFFF), EskerraChromeTokens.HeaderText)
    }

    @Test
    fun headerInactive_matchesRgbaWhite72Percent() {
        assertEquals(Color(0xB8FFFFFF), EskerraChromeTokens.HeaderInactive)
    }

    @Test
    fun listDivider_matchesSpec() {
        assertEquals(Color(0xFF333333), EskerraChromeTokens.ListDivider)
    }

    @Test
    fun modalBackground_matchesSpec() {
        assertEquals(Color(0xFF1D1D1D), EskerraChromeTokens.ModalBackground)
    }

    @Test
    fun modalTitle_matchesSpec() {
        assertEquals(Color(0xFFF5F5F5), EskerraChromeTokens.ModalTitle)
    }

    @Test
    fun subtitle_matchesSpec() {
        assertEquals(Color(0xFFB0B0B0), EskerraChromeTokens.Subtitle)
    }
}
