package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellTopScrimTest {
    @Test
    fun topScrimTranslationPx_atZero_translatesFullyAboveTop() {
        assertEquals(-100f, topScrimTranslationPx(revealedPx = 0f, fullPx = 100f))
    }

    @Test
    fun topScrimTranslationPx_halfway_translatesHalfway() {
        assertEquals(-50f, topScrimTranslationPx(revealedPx = 50f, fullPx = 100f))
    }

    @Test
    fun topScrimTranslationPx_atOrBeyondFull_isOnScreen() {
        assertEquals(0f, topScrimTranslationPx(revealedPx = 100f, fullPx = 100f))
        assertEquals(0f, topScrimTranslationPx(revealedPx = 250f, fullPx = 100f))
    }

    @Test
    fun topScrimTranslationPx_negativeRevealedPx_clampsToZero() {
        assertEquals(-100f, topScrimTranslationPx(revealedPx = -20f, fullPx = 100f))
    }

    @Test
    fun controller_withNoProviders_isFullyRevealed() {
        val controller = ShellTopScrimController()

        assertEquals(100f, controller.revealedPx(fullPx = 100f))
    }

    @Test
    fun controller_withOneProvider_reportsItsValueClamped() {
        val controller = ShellTopScrimController()
        val key = Any()

        controller.register(key) { 30f }
        assertEquals(30f, controller.revealedPx(fullPx = 100f))

        controller.register(key) { 250f }
        assertEquals(100f, controller.revealedPx(fullPx = 100f))
    }

    @Test
    fun controller_withMultipleProviders_reportsTheMinimum() {
        val controller = ShellTopScrimController()

        controller.register(Any()) { 80f }
        controller.register(Any()) { 20f }

        assertEquals(20f, controller.revealedPx(fullPx = 100f))
    }

    @Test
    fun controller_afterClear_dropsThatProvider() {
        val controller = ShellTopScrimController()
        val key = Any()

        controller.register(key) { 0f }
        assertEquals(0f, controller.revealedPx(fullPx = 100f))

        controller.clear(key)
        assertEquals(100f, controller.revealedPx(fullPx = 100f))
    }
}
