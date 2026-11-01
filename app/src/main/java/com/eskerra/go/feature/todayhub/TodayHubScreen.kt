package com.eskerra.go.feature.todayhub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.todayhub.TodayHubWeeks
import com.eskerra.go.ui.DateNavigationBar
import com.eskerra.go.ui.markdown.VaultMarkdownView
import com.eskerra.go.ui.theme.EskerraHeadingH1
import java.io.File

/**
 * Top chrome row for the home screen (spec §11): hub folder title and hub switcher.
 */
@Composable
fun TodayHubHeader(
    state: TodayHubUiState,
    onSelectHub: (NoteId) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = state as? TodayHubUiState.Content
    var pickerVisible by remember { mutableStateOf(false) }

    HubHeader(
        folderLabel = content?.folderLabel.orEmpty(),
        showPicker = content?.showHubPicker == true,
        onClick = {
            if (content?.showHubPicker == true) {
                pickerVisible = true
            } else {
                content?.activeHubId?.let(onSelectHub)
            }
        },
        modifier = modifier
    )

    if (pickerVisible && content != null) {
        TodayHubPickerSheet(
            hubs = content.hubs,
            activeHubId = content.activeHubId,
            onPickHub = { picked ->
                pickerVisible = false
                onSelectHub(picked)
            },
            onDismiss = { pickerVisible = false }
        )
    }
}

/**
 * Home screen body below the inbox list (spec §11): week selector, hub intro, and week columns.
 * Renders inline loading/empty/error states when the hub isn't ready. Top chrome lives in
 * [TodayHubHeader]; parent owns scroll.
 */
@Composable
fun TodayHubBody(
    state: TodayHubUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit = {},
    workspaceRoot: File? = null,
    modifier: Modifier = Modifier
) {
    when (state) {
        TodayHubUiState.Loading -> InlineStatusMessage(
            spinner = true,
            body = "Loading vault…",
            modifier = modifier
        )
        TodayHubUiState.Empty -> InlineStatusMessage(
            spinner = false,
            body = "Open search to browse notes in this vault.",
            modifier = modifier
        )
        is TodayHubUiState.Error -> InlineStatusMessage(
            spinner = false,
            body = state.message,
            onRetry = onRetry,
            modifier = modifier
        )
        is TodayHubUiState.Content -> TodayHubBodyContent(
            state = state,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onOpenInternalNote = onOpenInternalNote,
            onOpenExternalUrl = onOpenExternalUrl,
            onAmbiguousWikiLink = onAmbiguousWikiLink,
            onNoteNotFound = onNoteNotFound,
            workspaceRoot = workspaceRoot,
            modifier = modifier
        )
    }
}

@Composable
private fun TodayHubBodyContent(
    state: TodayHubUiState.Content,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    workspaceRoot: File?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
        DateNavigationBar(
            previousDescription = "Previous week",
            nextDescription = "Next week",
            label = state.weekRangeLabel,
            canGoPrev = state.canGoPrev,
            canGoNext = state.canGoNext,
            onPrevious = onPreviousWeek,
            onNext = onNextWeek
        )

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
}

@Composable
private fun InlineStatusMessage(
    spinner: Boolean,
    body: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun HubHeader(
    folderLabel: String,
    showPicker: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = folderLabel,
                style = EskerraHeadingH1,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = 2.dp)
            )
            if (showPicker) {
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = "Switch hub",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
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
                preserveLineBreaks = true,
                onNoteNotFound = onNoteNotFound,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
