package com.eskerra.go.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.model.GitStatusSummary
import kotlinx.coroutines.delay

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
            NoteEditorUiState.Loading -> EditorLoading()
            is NoteEditorUiState.Content -> EditorContent(
                state = state,
                onDraftChange = onDraftChange,
                onSave = onSave,
                modifier = Modifier.weight(1f)
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
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
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
            is CreateInboxUiState.Content -> CreateInboxContent(
                state = state,
                onDraftChange = onDraftChange,
                onSave = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CreateInboxContent(
    state: CreateInboxUiState.Content,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(CREATE_INBOX_FOCUS_DELAY_MS)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        BorderlessComposeEditor(
            value = state.draft,
            onValueChange = onDraftChange,
            placeholder = "First line is title (H1)...",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            readOnly = state.isSaving,
            focusRequester = focusRequester
        )

        EditorSaveBar(
            buttonText = if (state.isSaving) "Saving…" else "Save",
            enabled = state.canSave && !state.isSaving,
            message = state.errorMessage,
            messageIsError = true,
            onSave = onSave
        )
    }
}

@Composable
private fun BorderlessComposeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val scrollState = rememberScrollState()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .verticalScroll(scrollState),
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = placeholderColor
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun EditorLoading() {
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
private fun EditorContent(
    state: NoteEditorUiState.Content,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (state.note.canEdit) 12.dp else chrome.bottom)
        ) {
            Text(
                text = state.note.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                modifier = Modifier.fillMaxWidth(),
                readOnly = !state.note.canEdit || state.isSaving,
                minLines = 12,
                label = { Text("Markdown") }
            )
        }

        if (state.note.canEdit) {
            EditorSaveBar(
                buttonText = if (state.isSaving) "Saving…" else "Save",
                enabled = !state.isSaving && state.isDirty,
                message = state.errorMessage ?: state.saveMessage,
                messageIsError = state.errorMessage != null,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun EditorSaveBar(
    buttonText: String,
    enabled: Boolean,
    message: String?,
    messageIsError: Boolean,
    onSave: () -> Unit
) {
    val bottomPadding = editorBottomActionPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(top = 12.dp, bottom = bottomPadding)
    ) {
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.widthIn(min = 96.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun editorBottomActionPadding() =
    if (WindowInsets.ime.asPaddingValues().calculateBottomPadding() >= 10.dp) {
        0.dp
    } else {
        LocalShellChromeInsets.current.bottom
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

/** Matches notebox AddNoteScreen Android focus delay after navigation settles. */
private const val CREATE_INBOX_FOCUS_DELAY_MS = 250L
