package com.eskerra.go.feature.todayhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.todayhub.TodayHubWeeks
import com.eskerra.go.ui.markdown.VaultMarkdownView
import java.io.File

/**
 * Stateless Today Hub screen (spec §11). Renders the active hub intro and the selected week's
 * columns through the shared §8 markdown renderer, with week navigation and an optional hub picker.
 * All state and navigation are reported through callbacks.
 */
@Composable
fun TodayHubScreen(
    state: TodayHubUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectHub: (NoteId) -> Unit,
    onRetry: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    workspaceRoot: File? = null,
    modifier: Modifier = Modifier
) {
    val chrome = LocalShellChromeInsets.current
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            TodayHubUiState.Loading -> CenteredMessage(spinner = true, body = "Loading vault…")
            TodayHubUiState.Empty -> CenteredMessage(
                spinner = false,
                body = "Open search to browse notes in this vault."
            )
            is TodayHubUiState.Error -> CenteredMessage(
                spinner = false,
                body = state.message,
                onRetry = onRetry
            )
            is TodayHubUiState.Content -> TodayHubContent(
                state = state,
                topInset = chrome.top,
                bottomInset = chrome.bottom,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onSelectHub = onSelectHub,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                onNoteNotFound = onNoteNotFound,
                onOpenSearch = onOpenSearch,
                workspaceRoot = workspaceRoot
            )
        }
    }
}

@Composable
private fun TodayHubContent(
    state: TodayHubUiState.Content,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectHub: (NoteId) -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    onOpenSearch: () -> Unit,
    workspaceRoot: File?
) {
    var pickerVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topInset, bottom = bottomInset, start = 16.dp, end = 16.dp)
    ) {
        HubHeader(
            folderLabel = state.folderLabel,
            showPicker = state.showHubPicker,
            onOpenPicker = { pickerVisible = true },
            onOpenSearch = onOpenSearch
        )
        WeekNavBar(
            label = state.weekRangeLabel,
            canGoPrev = state.canGoPrev,
            canGoNext = state.canGoNext,
            onPrevious = onPreviousWeek,
            onNext = onNextWeek
        )

        if (state.introMarkdown.isNotBlank()) {
            VaultMarkdownView(
                markdown = state.introMarkdown,
                registry = state.registry,
                indexStatus = VaultReadonlyLink.IndexStatus.READY,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                workspaceRoot = workspaceRoot,
                sourceNoteId = state.activeHubId,
                onNoteNotFound = onNoteNotFound,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (state.rowLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.columnHeaders.forEachIndexed { index, header ->
            HubColumn(
                header = header,
                progressSegments = if (index == 0) state.progressSegments else emptyList(),
                body = state.row?.columns?.getOrNull(index).orEmpty(),
                rowNoteId = state.row?.rowNoteId,
                registry = state.registry,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                onNoteNotFound = onNoteNotFound,
                workspaceRoot = workspaceRoot
            )
        }
    }

    if (pickerVisible) {
        TodayHubPickerSheet(
            hubs = state.hubs,
            activeHubId = state.activeHubId,
            onPickHub = { picked ->
                pickerVisible = false
                onSelectHub(picked)
            },
            onDismiss = { pickerVisible = false }
        )
    }
}

@Composable
private fun HubHeader(
    folderLabel: String,
    showPicker: Boolean,
    onOpenPicker: () -> Unit,
    onOpenSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = folderLabel,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search vault",
                tint = com.eskerra.go.ui.theme.EskerraChromeTokens.HeaderText
            )
        }
        if (showPicker) {
            TextButton(onClick = onOpenPicker) {
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Switch hub")
            }
        }
    }
}

@Composable
private fun WeekNavBar(
    label: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = canGoPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous week"
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next week"
            )
        }
    }
}

@Composable
private fun HubColumn(
    header: String,
    progressSegments: List<TodayHubWeeks.ProgressSegment>,
    body: String,
    rowNoteId: NoteId?,
    registry: NoteRegistry,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    workspaceRoot: File?
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (progressSegments.isNotEmpty()) {
            TodayHubWeekProgressStrip(
                segments = progressSegments,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (body.isBlank()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            VaultMarkdownView(
                markdown = body,
                registry = registry,
                indexStatus = VaultReadonlyLink.IndexStatus.READY,
                onOpenInternalNote = onOpenInternalNote,
                onOpenExternalUrl = onOpenExternalUrl,
                onAmbiguousWikiLink = onAmbiguousWikiLink,
                workspaceRoot = workspaceRoot,
                sourceNoteId = rowNoteId,
                onNoteNotFound = onNoteNotFound,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CenteredMessage(spinner: Boolean, body: String, onRetry: (() -> Unit)? = null) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = chrome.top, bottom = chrome.bottom, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("Retry")
            }
        }
    }
}
