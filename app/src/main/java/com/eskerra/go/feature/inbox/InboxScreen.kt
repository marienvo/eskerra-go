package com.eskerra.go.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.datetime.RelativeCalendarLabel
import com.eskerra.go.core.inbox.InboxTileColor
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.feature.todayhub.TodayHubBody
import com.eskerra.go.feature.todayhub.TodayHubHeader
import com.eskerra.go.feature.todayhub.TodayHubUiState
import com.eskerra.go.ui.markdown.VaultMarkdownTokens
import com.eskerra.go.ui.theme.EskerraChromeTokens
import java.io.File

/** Fixed height for the shared top row so switching in/out of selection never shifts the list. */
private val InboxTopBarHeight = 56.dp

/**
 * Stateless home screen: inbox list (or empty/error) with Today Hub below.
 * Receives UI state and callbacks only; it knows nothing about where data comes from.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    todayHubState: TodayHubUiState,
    selectedNoteIds: Set<NoteId>,
    isDeleting: Boolean,
    deleteError: String?,
    onRetry: () -> Unit,
    onNoteClick: (NoteId) -> Unit,
    onAvatarClick: (NoteId) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectHub: (NoteId) -> Unit,
    onRetryTodayHub: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit = {},
    workspaceRoot: File? = null,
    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    InboxScrollBody(
        state = state,
        todayHubState = todayHubState,
        selectedNoteIds = selectedNoteIds,
        isDeleting = isDeleting,
        deleteError = deleteError,
        onNoteClick = onNoteClick,
        onAvatarClick = onAvatarClick,
        onClearSelection = onClearSelection,
        onDeleteSelected = onDeleteSelected,
        onRetry = onRetry,
        onPreviousWeek = onPreviousWeek,
        onNextWeek = onNextWeek,
        onSelectHub = onSelectHub,
        onRetryTodayHub = onRetryTodayHub,
        onOpenInternalNote = onOpenInternalNote,
        onOpenExternalUrl = onOpenExternalUrl,
        onAmbiguousWikiLink = onAmbiguousWikiLink,
        onNoteNotFound = onNoteNotFound,
        workspaceRoot = workspaceRoot,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
    )
}

@Composable
private fun InboxScrollBody(
    state: InboxUiState,
    todayHubState: TodayHubUiState,
    selectedNoteIds: Set<NoteId>,
    isDeleting: Boolean,
    deleteError: String?,
    onNoteClick: (NoteId) -> Unit,
    onAvatarClick: (NoteId) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRetry: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectHub: (NoteId) -> Unit,
    onRetryTodayHub: () -> Unit,
    onOpenInternalNote: (NoteId) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAmbiguousWikiLink: (List<NoteId>, String) -> Unit,
    onNoteNotFound: (String) -> Unit,
    workspaceRoot: File?,
    scrollToTopSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val hasSelection = selectedNoteIds.isNotEmpty()
    val listState = rememberLazyListState()

    // Home re-selection from a drill-down bumps the signal; jump the list back to the top.
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = shellScrollContentPadding()
    ) {
        // Top row shares one slot: selection actions while notes are selected, otherwise the hub
        // chrome (title, hub switcher). Both branches share InboxTopBarHeight so toggling selection
        // never shifts the list below.
        item {
            if (hasSelection) {
                InboxSelectionBar(
                    selectedCount = selectedNoteIds.size,
                    isDeleting = isDeleting,
                    onClearSelection = onClearSelection,
                    onDeleteSelected = onDeleteSelected
                )
            } else {
                TodayHubHeader(
                    state = todayHubState,
                    onSelectHub = onSelectHub,
                    modifier = Modifier
                        .height(InboxTopBarHeight)
                        .padding(horizontal = 16.dp)
                )
            }
        }

        deleteError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        when (state) {
            InboxUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is InboxUiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            InboxUiState.Empty -> {
                item {
                    Text(
                        text = "No inbox notes yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
            is InboxUiState.Content -> {
                items(state.notes) { note ->
                    InboxRow(
                        note = note,
                        isSelected = note.id in selectedNoteIds,
                        isDeleting = isDeleting,
                        onAvatarClick = { onAvatarClick(note.id) },
                        onClick = { onNoteClick(note.id) }
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                color = EskerraChromeTokens.ListDivider,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (state !is InboxUiState.Loading || todayHubState is TodayHubUiState.Content) {
            item {
                TodayHubBody(
                    state = todayHubState,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onRetry = onRetryTodayHub,
                    onOpenInternalNote = onOpenInternalNote,
                    onOpenExternalUrl = onOpenExternalUrl,
                    onAmbiguousWikiLink = onAmbiguousWikiLink,
                    onNoteNotFound = onNoteNotFound,
                    workspaceRoot = workspaceRoot,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun InboxSelectionBar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(InboxTopBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClearSelection, enabled = !isDeleting) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Clear selection",
                tint = Color.White
            )
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDeleteSelected, enabled = !isDeleting) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete selected",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun InboxRow(
    note: NoteSummary,
    isSelected: Boolean,
    isDeleting: Boolean,
    onAvatarClick: () -> Unit,
    onClick: () -> Unit
) {
    val mutedColor = VaultMarkdownTokens.Muted
    val tileColor = parseHexColor(
        InboxTileColor.backgroundColor(note.lastModifiedEpochMillis)
    )
    val dividerColor = EskerraChromeTokens.ListDivider

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isDeleting, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tileColor, RoundedCornerShape(8.dp))
                    .clickable(enabled = !isDeleting, onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = note.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = RelativeCalendarLabel.format(note.lastModifiedEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        HorizontalDivider(color = dividerColor)
    }
}

private fun parseHexColor(hex: String): Color {
    val normalized = hex.removePrefix("#")
    val value = normalized.toLong(16)
    return Color(
        red = ((value shr 16) and 0xFF) / 255f,
        green = ((value shr 8) and 0xFF) / 255f,
        blue = (value and 0xFF) / 255f
    )
}
