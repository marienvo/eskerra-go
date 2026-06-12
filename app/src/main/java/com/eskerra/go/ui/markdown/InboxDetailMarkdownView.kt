package com.eskerra.go.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import com.eskerra.go.core.markdown.CalloutBlocks
import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Inbox-detail renderer (spec §5.2): a deliberately limited renderer that strips frontmatter and
 * supports callouts only. Links are flat blue ([VaultMarkdownTokens.InboxDetailLink]) with **no**
 * wiki resolution, vault rules, reminder pills, or images — do not route this through
 * [VaultMarkdownView].
 */
@Composable
fun InboxDetailMarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val segments = remember(markdown) {
        val body = VaultMarkdownPreprocess.splitYamlFrontmatter(markdown).body
        if (body.isBlank()) emptyList() else CalloutBlocks.segment(body)
    }

    if (segments.isEmpty()) {
        Text(text = "Empty entry", fontStyle = FontStyle.Italic, modifier = modifier)
        return
    }

    val colors = markdownColor()
    val typography = markdownTypography(
        textLink = TextLinkStyles(style = SpanStyle(color = VaultMarkdownTokens.InboxDetailLink))
    )

    Column(modifier) {
        segments.forEach { segment ->
            when (segment) {
                is CalloutBlocks.Segment.Markdown -> Markdown(
                    content = segment.text,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.fillMaxWidth()
                )

                is CalloutBlocks.Segment.Callout -> CalloutCard(
                    callout = segment,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}
