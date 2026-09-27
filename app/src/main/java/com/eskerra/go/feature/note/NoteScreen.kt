package com.eskerra.go.feature.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.markdown.LazyNoteBlocks
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.PreparedSegment
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.data.perf.NoteNavTrace
import com.eskerra.go.ui.markdown.VaultMarkdownAnnotator
import com.eskerra.go.ui.markdown.VaultMarkdownSegmentContent
import com.eskerra.go.ui.markdown.vaultMarkdownComponents
import com.eskerra.go.ui.markdown.vaultMarkdownTypography
import com.mikepenz.markdown.m3.markdownColor
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Stateless read-only note reader. Renders precomputed [NoteReaderUiState] through the shared §8
 * markdown renderer and reports navigation through callbacks only.
 *
 * Back navigation is a floating shell button (see `AppShell`'s `onBack`), not part of this
 * screen's content, so the whole note scrolls underneath it and the hamburger.
 */
@Composable
fun NoteScreen(
    state: NoteReaderUiState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit = {},
    workspaceRoot: File? = null,
    onViewportChanged: (
        visibleStartFraction: Float,
        visibleEndFraction: Float
    ) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            NoteReaderUiState.Loading -> NoteReaderLoading()
            is NoteReaderUiState.Content -> NoteReaderContent(
                title = state.title,
                path = state.path,
                canEdit = state.canEdit,
                preparedBody = state.preparedBody,
                registry = state.document.registry,
                sourceNoteId = state.document.note.id,
                onEdit = onEdit,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                onNoteNotFound = onNoteNotFound,
                workspaceRoot = workspaceRoot,
                onViewportChanged = onViewportChanged
            )
            NoteReaderUiState.NotFound -> NoteReaderMessage(
                title = "Note not found",
                body = "This note is not in the workspace.",
                onRetry = null
            )
            NoteReaderUiState.InvalidNoteId -> NoteReaderMessage(
                title = "Invalid note",
                body = "This note path is not valid.",
                onRetry = null
            )
            is NoteReaderUiState.Error -> NoteReaderMessage(
                title = "Could not open note",
                body = state.message,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun NoteReaderLoading() {
    // Neutral background: warm opens complete before first composition so this is never seen;
    // cold misses show the theme surface color until Content is ready.
    Box(modifier = Modifier.fillMaxSize())
}

/**
 * A note's title/path/Edit button plus its body, all in one [LazyColumn] so title and body scroll
 * together under the floating back button/hamburger exactly as before — but only nearby items are
 * composed. [LazyNoteBlocks.split] breaks the pre-parsed body into small segments (splitting a long
 * bullet list into chunks), which is what actually shrinks first-frame cost for a long note: no
 * amount of caching helps once the whole body has to be composed and laid out in one non-lazy
 * `Column`, and that composition cost is the dominant one for a long note
 * (specs/performance/note-switching-logbook.md).
 */
@Composable
private fun NoteReaderContent(
    title: String,
    path: String,
    canEdit: Boolean,
    preparedBody: PreparedMarkdown,
    registry: NoteRegistry,
    sourceNoteId: NoteId,
    onEdit: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    workspaceRoot: File?,
    onViewportChanged: (visibleStartFraction: Float, visibleEndFraction: Float) -> Unit
) {
    val chrome = LocalShellChromeInsets.current
    val listState = rememberLazyListState()
    val segments = remember(preparedBody) { LazyNoteBlocks.split(preparedBody) }

    // Debug-only: marks the first frame this note's content is actually on screen, so a logcat
    // read can measure composition + layout cost (reader.published -> reader.firstFrame) — the
    // part no cache can shrink. Keyed on sourceNoteId so it fires once per distinct note shown,
    // not on every unrelated recomposition.
    LaunchedEffect(sourceNoteId) {
        withFrameNanos { }
        NoteNavTrace.log("reader.firstFrame", "noteId=${sourceNoteId.value}")
    }
    // Reports which slice of segments is visible (0f = top, 1f = bottom) so background prefetch
    // follows scrolling. Debounced: this is a priority hint, not something needing per-frame react.
    LaunchedEffect(listState, segments) {
        val segmentCount = segments.size
        if (segmentCount == 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .debounce(150)
            .collect { visible ->
                val visibleSegmentIndexes = visible
                    .map { it.index - NOTE_READER_HEADER_ITEM_COUNT }
                    .filter { it in 0 until segmentCount }
                if (visibleSegmentIndexes.isEmpty()) return@collect
                val start = visibleSegmentIndexes.min().toFloat() / segmentCount
                val end = (visibleSegmentIndexes.max() + 1).toFloat() / segmentCount
                onViewportChanged(start, end)
            }
    }

    val now = remember { LocalDateTime.now() }
    val colors = markdownColor()
    val typography = vaultMarkdownTypography()
    val onLinkTap: (String) -> Unit = { href ->
        when (val target = VaultReadonlyLink.targetFor(href, registry, sourceNoteId)) {
            is VaultReadonlyLink.LinkTarget.Internal -> onOpenInternalNote(target.noteId)
            is VaultReadonlyLink.LinkTarget.External -> onOpenExternalUrl(target.url)
            is VaultReadonlyLink.LinkTarget.Ambiguous ->
                onAmbiguousWikiLink(target.candidates, target.inner)
            // The reader always opens with a ready registry (see LoadNoteForReading), so unlike
            // VaultMarkdownView's other callers this never needs to distinguish a loading/error index.
            VaultReadonlyLink.LinkTarget.Unresolved -> onNoteNotFound("Note not found")
        }
    }
    val annotator = VaultMarkdownAnnotator.build(
        registry,
        VaultReadonlyLink.IndexStatus.READY,
        now,
        onLinkTap,
        sourceNoteId,
        preserveLineBreaks = false
    )
    val components = vaultMarkdownComponents(workspaceRoot, sourceNoteId)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = chrome.top,
            bottom = chrome.bottom,
            start = 16.dp,
            end = 16.dp
        )
    ) {
        item(key = "title") {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }
        item(key = "path") {
            Column {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (canEdit) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text("Edit")
                    }
                }
            }
        }
        itemsIndexed(
            items = segments,
            key = { index, segment -> noteSegmentKey(index, segment) }
        ) { _, segment ->
            VaultMarkdownSegmentContent(
                segment = segment,
                colors = colors,
                typography = typography,
                annotator = annotator,
                components = components,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val NOTE_READER_HEADER_ITEM_COUNT = 2

private fun noteSegmentKey(index: Int, segment: PreparedSegment): String {
    val offset = (segment as? PreparedSegment.Markdown)
        ?.state
        ?.let { it as? com.mikepenz.markdown.model.State.Success }
        ?.node
        ?.startOffset ?: -1
    return "segment-$index-$offset"
}

@Composable
private fun NoteReaderMessage(title: String, body: String, onRetry: (() -> Unit)?) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = chrome.top, bottom = chrome.bottom, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
