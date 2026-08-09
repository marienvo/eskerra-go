package com.eskerra.go.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandHueTest {
    @Test
    fun withBrandRedHue_preservesSaturationBrightnessAndAlpha() {
        val sources = listOf(
            Color(0xFFD0BCFF),
            Color(0xFFCCC2DC),
            Color(0xFFEFB8C8),
            Color(0xFF49454F),
            Color(0xFFE6E1E5),
            Color(0x801E1E1E)
        )

        for (source in sources) {
            val shifted = source.withBrandRedHue()
            assertEquals(maxChannel(source), maxChannel(shifted), CHANNEL_TOLERANCE)
            assertEquals(minChannel(source), minChannel(shifted), CHANNEL_TOLERANCE)
            assertEquals(source.alpha, shifted.alpha, CHANNEL_TOLERANCE)
            assertBrandRedOrNeutral(shifted)
        }
    }

    @Test
    fun colorSchemes_haveNoPurpleMaterialRoles() {
        for (scheme in listOf(EskerraDarkColorScheme, EskerraLightColorScheme)) {
            brandRoles(scheme).forEach(::assertBrandRedOrNeutral)
        }
    }

    @Test
    fun darkTheme_matchesBrandCursorAndNoteInputSurface() {
        assertEquals(0xFFFFBCBC.toInt(), EskerraDarkColorScheme.primary.toArgb())
        assertEquals(
            0xFF312B2B.toInt(),
            EskerraDarkColorScheme.surfaceColorAtElevation(3.dp).toArgb()
        )
    }

    @Test
    fun semanticErrorPalette_isNotHueShifted() {
        assertEquals(0xFFF2B8B5.toInt(), EskerraDarkColorScheme.error.toArgb())
        assertEquals(0xFFB3261E.toInt(), EskerraLightColorScheme.error.toArgb())
    }

    private fun assertBrandRedOrNeutral(color: Color) {
        val maximum = maxChannel(color)
        val minimum = minChannel(color)
        if (maximum - minimum <= CHANNEL_TOLERANCE) return
        assertEquals(maximum, color.red, CHANNEL_TOLERANCE)
        assertEquals(minimum, color.green, CHANNEL_TOLERANCE)
        assertEquals(minimum, color.blue, CHANNEL_TOLERANCE)
        assertTrue(color.red > color.green)
    }

    private fun brandRoles(scheme: ColorScheme): List<Color> = listOf(
        scheme.primary,
        scheme.onPrimary,
        scheme.primaryContainer,
        scheme.onPrimaryContainer,
        scheme.inversePrimary,
        scheme.secondary,
        scheme.onSecondary,
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
        scheme.tertiary,
        scheme.onTertiary,
        scheme.tertiaryContainer,
        scheme.onTertiaryContainer,
        scheme.background,
        scheme.onBackground,
        scheme.surface,
        scheme.onSurface,
        scheme.surfaceVariant,
        scheme.onSurfaceVariant,
        scheme.surfaceTint,
        scheme.inverseSurface,
        scheme.inverseOnSurface,
        scheme.outline,
        scheme.outlineVariant,
        scheme.surfaceBright,
        scheme.surfaceDim,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainerLowest
    )

    private fun maxChannel(color: Color): Float = maxOf(color.red, color.green, color.blue)

    private fun minChannel(color: Color): Float = minOf(color.red, color.green, color.blue)

    private companion object {
        const val CHANNEL_TOLERANCE = 0.000_001f
    }
}
