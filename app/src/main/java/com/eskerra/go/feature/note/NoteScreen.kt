package com.eskerra.go.feature.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.ui.markdown.VaultMarkdownView
import java.io.File

/**
 * Stateless read-only note reader. Renders precomputed [NoteReaderUiState] through the shared §8
 * markdown renderer and reports navigation through callbacks only.
 */
@Composable
fun NoteScreen(
    state: NoteReaderUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit = {},
    workspaceRoot: File? = null,
    modifier: Modifier = Modifier
) {
    val chrome = LocalShellChromeInsets.current
    Column(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(
                top = chrome.top,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        when (state) {
            NoteReaderUiState.Loading -> NoteReaderLoading()
            is NoteReaderUiState.Content -> NoteReaderContent(
                title = state.title,
                path = state.path,
                canEdit = state.canEdit,
                markdown = state.document.content.markdown,
                registry = state.document.registry,
                sourceNoteId = state.document.note.id,
                onEdit = onEdit,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                onNoteNotFound = onNoteNotFound,
                workspaceRoot = workspaceRoot
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
    val chrome = LocalShellChromeInsets.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoteReaderContent(
    title: String,
    path: String,
    canEdit: Boolean,
    markdown: String,
    registry: NoteRegistry,
    sourceNoteId: NoteId,
    onEdit: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    workspaceRoot: File?
) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
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
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp),
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
