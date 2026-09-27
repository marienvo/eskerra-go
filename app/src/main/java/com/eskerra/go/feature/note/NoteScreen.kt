package com.eskerra.go.feature.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.data.perf.NoteNavTrace
import com.eskerra.go.ui.markdown.VaultMarkdownView
import java.io.File
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
                markdown = state.bodyMarkdown,
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

@Composable
private fun NoteReaderContent(
    title: String,
    path: String,
    canEdit: Boolean,
    markdown: String,
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
    val scrollState = rememberScrollState()
    // Debug-only: marks the first frame this note's content is actually on screen, so a logcat
    // read can measure composition + layout cost (reader.published -> reader.firstFrame) — the
    // part no cache can shrink. Keyed on sourceNoteId so it fires once per distinct note shown,
    // not on every unrelated recomposition.
    LaunchedEffect(sourceNoteId) {
        withFrameNanos { }
        NoteNavTrace.log("reader.firstFrame", "noteId=${sourceNoteId.value}")
    }
    // Reports the top-of-viewport fraction (a point, not a window — cheap and close enough to
    // reorder background prefetch toward what's on screen) so link prefetch follows scrolling.
    // Debounced: this is a priority hint, not something that needs to react every frame.
    LaunchedEffect(scrollState) {
        snapshotFlow {
            if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
        }
            .distinctUntilChanged()
            .debounce(150)
            .collect { fraction -> onViewportChanged(fraction, fraction) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = chrome.top, bottom = chrome.bottom, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
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
        VaultMarkdownView(
            markdown = markdown,
            registry = registry,
            indexStatus = VaultReadonlyLink.IndexStatus.READY,
            onOpenInternalNote = onOpenInternalNote,
            onOpenExternalUrl = onOpenExternalUrl,
            onAmbiguousWikiLink = onAmbiguousWikiLink,
            workspaceRoot = workspaceRoot,
            sourceNoteId = sourceNoteId,
            onNoteNotFound = onNoteNotFound,
            preparedOverride = preparedBody,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
