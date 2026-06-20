package com.eskerra.go.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.PreparedSegment
import com.eskerra.go.core.markdown.prepareVaultMarkdown
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Inbox-detail renderer (spec §5.2): a deliberately limited renderer that strips frontmatter and
 * supports callouts only. Links are flat blue ([VaultMarkdownTokens.InboxDetailLink]) with **no**
 * wiki resolution, vault rules, reminder pills, or images — do not route this through
 * [VaultMarkdownView]. The body is pre-parsed off the main thread and rendered atomically.
 */
@Composable
fun InboxDetailMarkdownView(markdown: String, modifier: Modifier = Modifier) {
    var prepared by remember { mutableStateOf<PreparedMarkdown?>(null) }
    LaunchedEffect(markdown) {
        prepared = prepareVaultMarkdown(markdown, preprocessWikiLinks = false)
    }

    val body = prepared ?: return
    if (body.segments.isEmpty()) {
        Text(text = "Empty entry", fontStyle = FontStyle.Italic, modifier = modifier)
        return
    }

    val colors = markdownColor()
    val typography = markdownTypography(
        textLink = TextLinkStyles(style = SpanStyle(color = VaultMarkdownTokens.InboxDetailLink))
    )

    Column(modifier) {
        body.segments.forEach { segment ->
            when (segment) {
                is PreparedSegment.Markdown -> Markdown(
                    segment.state,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.fillMaxWidth()
                )

                is PreparedSegment.Callout -> CalloutCard(
                    resolved = segment.resolved,
                    title = segment.title,
                    body = segment.body,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}
