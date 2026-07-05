package com.eskerra.go.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.search.VaultSearchNoteResult
import com.eskerra.go.ui.theme.EskerraChromeTokens

@Composable
fun SearchScreen(
    state: SearchUiState,
    query: String,
    onOpenNote: (NoteId) -> Unit,
    onRetryIndex: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = chrome.top, bottom = chrome.bottom)
    ) {
        // The bottom shell pill is the search input; this page just renders status + results.
        SearchStatusLine(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (state) {
            SearchUiState.Idle -> SearchHint(text = "Type to search markdown in the vault.")
            is SearchUiState.Opening,
            is SearchUiState.Searching -> {
                val previous = previousResults(state)
                if (previous.isEmpty()) {
                    SearchLoading()
                } else {
                    SearchResultsList(
                        notes = previous,
                        query = query,
                        onOpenNote = onOpenNote
                    )
                }
            }
            is SearchUiState.Results -> SearchResultsList(
                notes = state.notes,
                query = state.query,
                onOpenNote = onOpenNote
            )
            is SearchUiState.NoMatches -> SearchHint(text = "No matches.")
            is SearchUiState.Error -> SearchErrorHint(
                message = state.message,
                detail = state.detail,
                canRetry = state.canRetry,
                onRetryIndex = onRetryIndex
            )
        }
    }
}

@Composable
private fun SearchStatusLine(state: SearchUiState, modifier: Modifier = Modifier) {
    val text = when (state) {
        SearchUiState.Idle -> null
        is SearchUiState.Opening -> "Opening search index…"
        is SearchUiState.Searching -> "Searching…"
        is SearchUiState.Results -> state.resultCountLabel
        is SearchUiState.NoMatches -> "No matches."
        is SearchUiState.Error -> state.message
    }
    val footer = when (state) {
        is SearchUiState.Results -> if (!state.bodiesIndexReady) {
            "Body text is still indexing; results may be partial."
        } else {
            null
        }
        is SearchUiState.NoMatches -> if (!state.bodiesIndexReady) {
            "Body text is still indexing; results may be partial."
        } else {
            null
        }
        else -> null
    }
    Column(modifier = modifier) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = EskerraChromeTokens.Subtitle
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = EskerraChromeTokens.Subtitle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SearchErrorHint(
    message: String,
    detail: String?,
    canRetry: Boolean,
    onRetryIndex: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = EskerraChromeTokens.Subtitle)
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = EskerraChromeTokens.Subtitle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (canRetry) {
            Button(
                onClick = onRetryIndex,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Retry indexing")
            }
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = EskerraChromeTokens.Subtitle)
    }
}

@Composable
private fun SearchLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SearchResultsList(
    notes: List<VaultSearchNoteResult>,
    query: String,
    onOpenNote: (NoteId) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(notes, key = { it.uri }) { note ->
            SearchResultRow(
                note = note,
                query = query,
                onClick = { onOpenNote(NoteId(note.uri)) }
            )
            HorizontalDivider(color = EskerraChromeTokens.ListDivider)
        }
    }
}

private fun previousResults(state: SearchUiState): List<VaultSearchNoteResult> = when (state) {
    is SearchUiState.Opening -> state.previousResults
    is SearchUiState.Searching -> state.previousResults
    else -> emptyList()
}
