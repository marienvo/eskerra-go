package com.eskerra.go.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SourceDarkColorScheme = darkColorScheme(
    primary = SourcePrimary80,
    secondary = SourceSecondary80,
    tertiary = SourceTertiary80,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceContainerHigh = DarkSurfaceContainerHigh
)
internal val EskerraDarkColorScheme = SourceDarkColorScheme.withBrandRedHue()

private val SourceLightColorScheme = lightColorScheme(
    primary = SourcePrimary40,
    secondary = SourceSecondary40,
    tertiary = SourceTertiary40
)
internal val EskerraLightColorScheme = SourceLightColorScheme.withBrandRedHue()

/** Dark-mode-only theme. Chrome/header/modal tokens live in [EskerraChromeTokens] (spec §10). */
@Composable
fun EskerraGoTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) EskerraDarkColorScheme else EskerraLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
