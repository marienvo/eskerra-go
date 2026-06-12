package com.eskerra.go.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.eskerra.go.core.markdown.CalloutBlocks
import com.eskerra.go.core.markdown.VaultMarkdownPreprocess
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import java.time.LocalDateTime

/**
 * Shared read-only vault markdown renderer (spec §8): YAML frontmatter strip, wiki→synthetic-link
 * preprocessing, standard markdown via the library, custom callout cards, per-tone link colours,
 * tap routing, and reminder pills.
 */
@Composable
fun VaultMarkdownView(
    markdown: String,
    registry: NoteRegistry,
    indexStatus: VaultReadonlyLink.IndexStatus,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember { LocalDateTime.now() }
    val segments = remember(markdown) {
        val split = VaultMarkdownPreprocess.splitYamlFrontmatter(markdown)
        val body = VaultMarkdownPreprocess.preprocessVaultReadonlyMarkdownBody(split.body)
        CalloutBlocks.segment(body)
    }

    val colors = markdownColor()
    val typography = markdownTypography()

    val onLinkTap: (String) -> Unit = { href ->
        when (val target = VaultReadonlyLink.targetFor(href, registry)) {
            is VaultReadonlyLink.LinkTarget.Internal -> onOpenInternalNote(target.noteId)
            is VaultReadonlyLink.LinkTarget.External -> onOpenExternalUrl(target.url)
            is VaultReadonlyLink.LinkTarget.Ambiguous ->
                onAmbiguousWikiLink(target.candidates, target.inner)
            VaultReadonlyLink.LinkTarget.Unresolved -> Unit
        }
    }
    val annotator = VaultMarkdownAnnotator.build(registry, indexStatus, now, onLinkTap)

    Column(modifier) {
        segments.forEach { segment ->
            when (segment) {
                is CalloutBlocks.Segment.Markdown -> Markdown(
                    content = segment.text,
                    colors = colors,
                    typography = typography,
                    annotator = annotator,
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
