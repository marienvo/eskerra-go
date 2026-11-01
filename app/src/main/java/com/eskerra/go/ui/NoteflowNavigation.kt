package com.eskerra.go.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import com.eskerra.go.core.todayhub.TodayHubDiscovery

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DatedFolderLinks(links: List<TodayHubDatedLink>, current: NoteId?, onOpen: (NoteId) -> Unit) {
    if (links.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        links.forEach { link ->
            val modifier = Modifier.semantics {
                contentDescription =
                    if (link.isIncomplete) "${link.folder}, unfinished" else link.folder
            }
            Text(
                text = link.folder + if (link.isIncomplete) " •" else "",
                fontWeight = if (current != null &&
                    TodayHubDiscovery.directoryOf(current.value) ==
                    TodayHubDiscovery.directoryOf(link.noteId.value)
                ) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                modifier = if (current ==
                    link.noteId
                ) {
                    modifier
                } else {
                    modifier.clickable { onOpen(link.noteId) }
                }
            )
        }
    }
}

@Composable
fun DateNavigationBar(
    label: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = canGoPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousDescription
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextDescription)
        }
    }
}
