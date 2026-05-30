package com.eskerra.go.feature.editor

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.model.GitStatusSummary

/**
 * Stateless markdown editor. Receives [NoteEditorUiState] and reports user actions
 * through callbacks only.
 */
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        when (state) {
            NoteEditorUiState.Loading -> EditorLoading()
            is NoteEditorUiState.Content -> EditorContent(
                state = state,
                onDraftChange = onDraftChange,
                onSave = onSave
            )
            NoteEditorUiState.NotFound -> EditorMessage(
                title = "Note not found",
                body = "This note is not in the workspace.",
                onRetry = null
            )
            NoteEditorUiState.InvalidNoteId -> EditorMessage(
                title = "Invalid note",
                body = "This note path is not valid.",
                onRetry = null
            )
            is NoteEditorUiState.Error -> EditorMessage(
                title = "Could not open editor",
                body = state.message,
                onRetry = onRetry
            )
        }
    }
}

@Composable
fun CreateInboxScreen(
    state: CreateInboxUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            CreateInboxUiState.Creating -> CircularProgressIndicator()
            is CreateInboxUiState.Error -> EditorMessage(
                title = "Could not create note",
                body = state.message,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun EditorLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EditorContent(
    state: NoteEditorUiState.Content,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = state.note.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = state.note.path.value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = state.gitStatus.formatLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = gitStatusColor(state.gitStatus),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (!state.note.canEdit) {
            Text(
                text = "This note is read-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        OutlinedTextField(
            value = state.draftMarkdown,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            readOnly = !state.note.canEdit || state.isSaving,
            minLines = 12,
            label = { Text("Markdown") }
        )

        if (state.note.canEdit) {
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.isDirty,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        }

        state.saveMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun gitStatusColor(status: GitStatusSummary) = when (status.state) {
    GitStatusSummary.State.Clean -> MaterialTheme.colorScheme.onSurfaceVariant
    GitStatusSummary.State.Dirty -> MaterialTheme.colorScheme.primary
    GitStatusSummary.State.Unavailable,
    GitStatusSummary.State.Error -> MaterialTheme.colorScheme.error
}

@Composable
private fun EditorMessage(title: String, body: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge
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
