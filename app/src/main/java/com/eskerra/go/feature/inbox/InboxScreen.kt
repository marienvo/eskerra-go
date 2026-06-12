package com.eskerra.go.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.datetime.RelativeCalendarLabel
import com.eskerra.go.core.inbox.InboxTileColor
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.ui.markdown.VaultMarkdownTokens
import com.eskerra.go.ui.theme.EskerraChromeTokens

/**
 * Stateless inbox list. Receives UI state and callbacks only; it knows nothing
 * about where the notes come from.
 */
@Composable
fun InboxScreen(
    state: InboxUiState,
    selectedNoteIds: Set<NoteId>,
    isDeleting: Boolean,
    deleteError: String?,
    onRetry: () -> Unit,
    onNoteClick: (NoteId) -> Unit,
    onAvatarClick: (NoteId) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    showRefreshIndicator: Boolean = false,
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
            selectedNoteIds = selectedNoteIds,
            isDeleting = isDeleting,
            deleteError = deleteError,
            showRefreshIndicator = showRefreshIndicator,
            onNoteClick = onNoteClick,
            onAvatarClick = onAvatarClick,
            onClearSelection = onClearSelection,
            onDeleteSelected = onDeleteSelected,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
    }
}

@Composable
private fun InboxScrollBody(
    state: InboxUiState,
    selectedNoteIds: Set<NoteId>,
    isDeleting: Boolean,
    deleteError: String?,
    showRefreshIndicator: Boolean,
    onNoteClick: (NoteId) -> Unit,
    onAvatarClick: (NoteId) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelection = selectedNoteIds.isNotEmpty()
    val chrome = LocalShellChromeInsets.current

    Column(modifier = modifier.fillMaxSize()) {
        InboxHeaderBar(
            hasSelection = hasSelection,
            selectedCount = selectedNoteIds.size,
            isDeleting = isDeleting,
            onClearSelection = onClearSelection,
            onDeleteSelected = onDeleteSelected,
            onOpenSettings = onOpenSettings,
            topInset = chrome.top
        )

        deleteError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = shellScrollContentPadding()
        ) {
            if (showRefreshIndicator) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }

            when (state) {
                InboxUiState.Empty -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No markdown entries found in Inbox. " +
                                    "Add one via the Entry tab.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    item { TodayHubPlaceholder() }
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
                    item { TodayHubPlaceholder() }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun InboxHeaderBar(
    hasSelection: Boolean,
    selectedCount: Int,
    isDeleting: Boolean,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset, start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSelection) {
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
        } else {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
