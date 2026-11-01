package com.eskerra.go.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.shellScrollContentPadding

@Composable
fun NotificationListScreen(noteIds: List<String>, onOpenNote: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(shellScrollContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(noteIds, key = { it }) { noteId ->
                Text(
                    text = noteId.substringAfterLast('/').removeSuffix(".md"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNote(noteId) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}
