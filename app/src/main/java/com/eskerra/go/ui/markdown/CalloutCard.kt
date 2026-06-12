package com.eskerra.go.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.markdown.CalloutBlocks
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Renders a single Obsidian callout block (spec §8) as a tinted card with an accent title and the
 * inner body rendered as standard markdown.
 */
@Composable
fun CalloutCard(
    callout: CalloutBlocks.Segment.Callout,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    modifier: Modifier = Modifier
) {
    val accent = VaultMarkdownTokens.calloutAccent(callout.resolved.color)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Column {
            Text(
                text = callout.title,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = if (callout.body.isEmpty()) 0.dp else 6.dp)
            )
            if (callout.body.isNotEmpty()) {
                Markdown(
                    content = callout.body,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
