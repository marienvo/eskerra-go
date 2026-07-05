package com.eskerra.go.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Canonical markdown heading scale (spec §8): one even ladder shared by every markdown renderer and
 * by the shell chrome that mirrors a heading level. H1 is also the hub title / selection-bar size;
 * each level steps down to H6, which meets the 16sp body size. Colour and weight match the previous
 * hub title (normal weight) so callers apply their own colour on top.
 */
private fun heading(sizeSp: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.25f).sp
)

val EskerraHeadingH1 = heading(28)
val EskerraHeadingH2 = heading(25)
val EskerraHeadingH3 = heading(22)
val EskerraHeadingH4 = heading(20)
val EskerraHeadingH5 = heading(18)
val EskerraHeadingH6 = heading(16)
