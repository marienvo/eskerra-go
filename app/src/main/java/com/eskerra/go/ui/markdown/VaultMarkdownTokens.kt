package com.eskerra.go.ui.markdown

import androidx.compose.ui.graphics.Color
import com.eskerra.go.core.markdown.CalloutHeader
import com.eskerra.go.core.markdown.DateToken
import com.eskerra.go.core.markdown.VaultReadonlyLink.LinkTone

/**
 * Dark-mode colour tokens for the read-only vault markdown renderer (spec §8.3 / §8.4) and the
 * reminder pill tones (derived from the desktop `dateTokenHighlight.css` buckets).
 */
object VaultMarkdownTokens {

    // §8.4 body typography
    val Body = Color(0xFFF5F5F5)
    val Muted = Color(0xFFCFCFCF)
    val CodeBackground = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
    val CodeBorder = Color(0x1FFFFFFF) // rgba(255,255,255,0.12)

    // §8.3 link colours
    val InternalNote = Color(0xFFFF8A82)
    val ExternalSite = Color(0xFF7DCCFF)

    /** §5.2 inbox-detail flat blue link colour (deliberately not the §8.3 tokens). */
    val InboxDetailLink = Color(0xFF4F9DFF)

    fun linkColor(tone: LinkTone): Color = when (tone) {
        LinkTone.INTERNAL -> InternalNote
        LinkTone.EXTERNAL -> ExternalSite
        LinkTone.MUTED -> Muted
    }

    /** Reminder pill background + foreground per tone. */
    data class PillColors(val background: Color, val foreground: Color)

    fun pillColors(tone: DateToken.PillTone): PillColors = when (tone) {
        DateToken.PillTone.COMPLETED -> PillColors(Color(0x33FFFFFF), Muted)
        DateToken.PillTone.PAST -> PillColors(Color(0x33FFFFFF), Muted)
        DateToken.PillTone.URGENT -> PillColors(Color(0x33FF6B6B), Color(0xFFFFB3AE))
        DateToken.PillTone.FUTURE -> PillColors(Color(0x33E6C200), Color(0xFFF2D750))
        DateToken.PillTone.NEUTRAL -> PillColors(Color(0x334F9DFF), Color(0xFF9FC8FF))
    }

    /** Callout accent colour for a resolved callout colour bucket. */
    fun calloutAccent(color: CalloutHeader.CalloutColor): Color = when (color) {
        CalloutHeader.CalloutColor.BLUE -> Color(0xFF4F9DFF)
        CalloutHeader.CalloutColor.CYAN -> Color(0xFF42C5D6)
        CalloutHeader.CalloutColor.TEAL -> Color(0xFF2DD4BF)
        CalloutHeader.CalloutColor.GREEN -> Color(0xFF5CC97A)
        CalloutHeader.CalloutColor.YELLOW -> Color(0xFFE6C200)
        CalloutHeader.CalloutColor.ORANGE -> Color(0xFFF2A04D)
        CalloutHeader.CalloutColor.RED -> Color(0xFFFF6B6B)
        CalloutHeader.CalloutColor.PURPLE -> Color(0xFFB57EDC)
        CalloutHeader.CalloutColor.GREY -> Color(0xFF9AA0A6)
    }
}
