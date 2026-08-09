package com.eskerra.go.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Moves an sRGB color to the brand-red hue while preserving HSB saturation and brightness. */
internal fun Color.withBrandRedHue(): Color {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    return Color(red = maximum, green = minimum, blue = minimum, alpha = alpha)
}

/**
 * Removes the inherited purple hue from every non-semantic Material role. Error colors retain
 * their semantic palette; scrim remains neutral.
 */
internal fun ColorScheme.withBrandRedHue(): ColorScheme = copy(
    primary = primary.withBrandRedHue(),
    onPrimary = onPrimary.withBrandRedHue(),
    primaryContainer = primaryContainer.withBrandRedHue(),
    onPrimaryContainer = onPrimaryContainer.withBrandRedHue(),
    inversePrimary = inversePrimary.withBrandRedHue(),
    secondary = secondary.withBrandRedHue(),
    onSecondary = onSecondary.withBrandRedHue(),
    secondaryContainer = secondaryContainer.withBrandRedHue(),
    onSecondaryContainer = onSecondaryContainer.withBrandRedHue(),
    tertiary = tertiary.withBrandRedHue(),
    onTertiary = onTertiary.withBrandRedHue(),
    tertiaryContainer = tertiaryContainer.withBrandRedHue(),
    onTertiaryContainer = onTertiaryContainer.withBrandRedHue(),
    background = background.withBrandRedHue(),
    onBackground = onBackground.withBrandRedHue(),
    surface = surface.withBrandRedHue(),
    onSurface = onSurface.withBrandRedHue(),
    surfaceVariant = surfaceVariant.withBrandRedHue(),
    onSurfaceVariant = onSurfaceVariant.withBrandRedHue(),
    surfaceTint = surfaceTint.withBrandRedHue(),
    inverseSurface = inverseSurface.withBrandRedHue(),
    inverseOnSurface = inverseOnSurface.withBrandRedHue(),
    outline = outline.withBrandRedHue(),
    outlineVariant = outlineVariant.withBrandRedHue(),
    surfaceBright = surfaceBright.withBrandRedHue(),
    surfaceDim = surfaceDim.withBrandRedHue(),
    surfaceContainer = surfaceContainer.withBrandRedHue(),
    surfaceContainerHigh = surfaceContainerHigh.withBrandRedHue(),
    surfaceContainerHighest = surfaceContainerHighest.withBrandRedHue(),
    surfaceContainerLow = surfaceContainerLow.withBrandRedHue(),
    surfaceContainerLowest = surfaceContainerLowest.withBrandRedHue()
)
