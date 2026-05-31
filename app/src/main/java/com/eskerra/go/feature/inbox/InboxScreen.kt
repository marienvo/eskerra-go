package com.eskerra.go.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/**
 * Stateless inbox list. Receives UI state and callbacks only; it knows nothing
 * about where the notes come from.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onRetry: () -> Unit,
    onNoteClick: (NoteId) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        InboxUiState.Loading -> InboxLoading(modifier)
        is InboxUiState.Error -> InboxError(
            message = state.message,
            onRetry = onRetry,
            modifier = modifier
        )
        InboxUiState.Empty,
        is InboxUiState.Content -> InboxScrollBody(
            state = state,
            onNoteClick = onNoteClick,
            modifier = modifier
        )
    }
}

@Composable
private fun InboxScrollBody(
    state: InboxUiState,
    onNoteClick: (NoteId) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = shellScrollContentPadding()
    ) {
        item {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when (state) {
            InboxUiState.Empty -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No inbox notes yet. Tap Add to create one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                item { TodayHubPlaceholder() }
            }
            is InboxUiState.Content -> {
                items(state.notes) { note ->
                    InboxRow(note = note, onClick = { onNoteClick(note.id) })
                }
                item { TodayHubPlaceholder() }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TodayHubPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = "placeholder todayhub",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun InboxLoading(modifier: Modifier = Modifier) {
    val chrome = LocalShellChromeInsets.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(chrome.asPaddingValues()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InboxError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(chrome.asPaddingValues())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Inbox",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun InboxRow(note: NoteSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (note.snippet.isNotBlank()) {
                Text(
                    text = note.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
