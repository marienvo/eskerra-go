package com.eskerra.go.feature.note

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DatedNoteTitle(
    title: String,
    isRead: Boolean,
    isLoading: Boolean,
    onToggleReadStatus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        val actionLabel = if (isRead) "Mark as unread" else "Mark as read"
        IconButton(
            onClick = onToggleReadStatus,
            enabled = !isLoading,
            modifier = Modifier.semantics { contentDescription = actionLabel }
        ) {
            Crossfade(targetState = isRead, label = "readStatusIcon") { read ->
                Text(text = if (read) "✅" else "⬜️", fontSize = 20.sp)
            }
        }
    }
}
