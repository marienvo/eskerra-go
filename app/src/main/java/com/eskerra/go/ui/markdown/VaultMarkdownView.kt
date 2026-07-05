package com.eskerra.go.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.PreparedSegment
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import java.io.File
import java.time.LocalDateTime

/**
 * Shared read-only vault markdown renderer (spec §8, §13): YAML frontmatter strip, wiki→synthetic-link
 * preprocessing, standard markdown via the library, custom callout cards, per-tone link colours,
 * vault image loading, tap routing, and reminder pills.
 *
 * The body is pre-parsed off the main thread (via [LocalParsedMarkdownCache]) and rendered as one
 * coherent unit: warm content paints fully on the first frame, and while new content parses the
 * previous body stays visible (no empty loading slot, no per-block pop-in). A true cold miss holds
 * on the neutral theme background until the body is ready.
 *
 * @param workspaceRoot vault working-tree root on disk; required for local attachment `file://` loads.
 * @param sourceNoteId vault-relative path of the open note; enables relative link/image resolution
 *   (spec §9.3, §13). Pass `null` when the source context is unknown (links show as muted).
 * @param preserveLineBreaks renders Markdown soft breaks as visible newlines. Intended for Today Hub
 *   cells, whose read-only representation mirrors the source's line-based editor.
 * @param onNoteNotFound called when a tapped link cannot be resolved; message reflects [indexStatus]
 *   ("Note not found", "Still indexing vault", "Vault index unavailable").
 */
@Composable
fun VaultMarkdownView(
    markdown: String,
    registry: NoteRegistry,
    indexStatus: VaultReadonlyLink.IndexStatus,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    modifier: Modifier = Modifier,
    workspaceRoot: File? = null,
    sourceNoteId: NoteId? = null,
    preserveLineBreaks: Boolean = false,
    onNoteNotFound: (String) -> Unit = {}
) {
    val now = remember { LocalDateTime.now() }
    val cache = LocalParsedMarkdownCache.current
    // Seed from the warm cache so warm content paints on the first frame; otherwise the previous
    // body stays visible (retain state) until the new body finishes parsing off the main thread.
    var prepared by remember { mutableStateOf<PreparedMarkdown?>(cache.peek(markdown)) }
    LaunchedEffect(markdown, cache) {
        prepared = cache.get(markdown)
    }

    val colors = markdownColor()
    val typography = vaultMarkdownTypography()

    val onLinkTap: (String) -> Unit = { href ->
        when (val target = VaultReadonlyLink.targetFor(href, registry, sourceNoteId)) {
            is VaultReadonlyLink.LinkTarget.Internal -> onOpenInternalNote(target.noteId)
            is VaultReadonlyLink.LinkTarget.External -> onOpenExternalUrl(target.url)
            is VaultReadonlyLink.LinkTarget.Ambiguous ->
                onAmbiguousWikiLink(target.candidates, target.inner)
            VaultReadonlyLink.LinkTarget.Unresolved -> onNoteNotFound(
                when (indexStatus) {
                    VaultReadonlyLink.IndexStatus.LOADING -> "Still indexing vault"
                    VaultReadonlyLink.IndexStatus.READY -> "Note not found"
                    VaultReadonlyLink.IndexStatus.ERROR -> "Vault index unavailable"
                }
            )
        }
    }
    val annotator = VaultMarkdownAnnotator.build(
        registry,
        indexStatus,
        now,
        onLinkTap,
        sourceNoteId,
        preserveLineBreaks
    )
    val components = vaultMarkdownComponents(workspaceRoot, sourceNoteId)

    Column(modifier) {
        prepared?.segments?.forEach { segment ->
            when (segment) {
                is PreparedSegment.Markdown -> Markdown(
                    segment.state,
                    colors = colors,
                    typography = typography,
                    annotator = annotator,
                    components = components,
                    modifier = Modifier.fillMaxWidth()
                )

                is PreparedSegment.Callout -> CalloutCard(
                    resolved = segment.resolved,
                    title = segment.title,
                    body = segment.body,
                    colors = colors,
                    typography = typography,
                    workspaceRoot = workspaceRoot,
                    sourceNoteId = sourceNoteId,
                    preserveLineBreaks = preserveLineBreaks
                )
            }
        }
    }
}
