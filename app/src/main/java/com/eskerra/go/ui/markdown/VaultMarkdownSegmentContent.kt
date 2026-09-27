package com.eskerra.go.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eskerra.go.core.markdown.PreparedSegment
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Renders one [PreparedSegment] — shared by [VaultMarkdownView] (a whole note's segments in one
 * `Column`) and [com.eskerra.go.feature.note.NoteScreen]'s lazy reader (each segment its own
 * `LazyColumn` item, after [com.eskerra.go.core.markdown.LazyNoteBlocks] has split a note's long
 * runs into smaller segments). Kept in one place so both call the exact same
 * `Markdown(state, colors, typography, annotator, components, modifier)` — the call already proven
 * correct throughout this app — instead of two copies drifting apart.
 */
@Composable
internal fun VaultMarkdownSegmentContent(
    segment: PreparedSegment,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    annotator: MarkdownAnnotator,
    components: MarkdownComponents,
    modifier: Modifier = Modifier
) {
    when (segment) {
        is PreparedSegment.Markdown -> Markdown(
            segment.state,
            colors = colors,
            typography = typography,
            annotator = annotator,
            components = components,
            modifier = modifier.fillMaxWidth()
        )

        is PreparedSegment.Callout -> CalloutCardContent(
            resolved = segment.resolved,
            title = segment.title,
            body = segment.body,
            colors = colors,
            typography = typography,
            annotator = annotator,
            components = components,
            modifier = modifier
        )
    }
}
