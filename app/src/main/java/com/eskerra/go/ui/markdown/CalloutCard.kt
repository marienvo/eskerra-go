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
import com.eskerra.go.core.markdown.CalloutHeader
import com.eskerra.go.core.model.NoteId
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import java.io.File

/**
 * Renders a single Obsidian callout block (spec §8) as a tinted card with an accent title and the
 * inner body rendered as standard markdown. The body is pre-parsed (see
 * [com.eskerra.go.core.markdown.PreparedSegment.Callout]) so it paints together with the rest of the
 * note; [body] is `null` when the callout has no body.
 */
@Composable
fun CalloutCard(
    resolved: CalloutHeader.ResolvedCallout,
    title: String,
    body: State?,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    modifier: Modifier = Modifier,
    workspaceRoot: File? = null,
    sourceNoteId: NoteId? = null,
    annotator: MarkdownAnnotator? = null,
    preserveLineBreaks: Boolean = false
) {
    val accent = VaultMarkdownTokens.calloutAccent(resolved.color)
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
                text = title,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = if (body == null) 0.dp else 6.dp)
            )
            if (body != null) {
                Markdown(
                    body,
                    colors = colors,
                    typography = typography,
                    annotator = annotator ?: markdownAnnotator(
                        config = markdownAnnotatorConfig(eolAsNewLine = preserveLineBreaks)
                    ),
                    components = vaultMarkdownComponents(workspaceRoot, sourceNoteId),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
