package com.eskerra.go.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary

/**
 * Minimal manual sync screen. Receives state and callbacks only; it does not
 * call Git or read files directly.
 */
@Composable
fun SyncScreen(
    state: SyncUiState,
    onSyncNow: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sync",
            style = MaterialTheme.typography.headlineMedium
        )

        when (state) {
            SyncUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading sync status…",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            is SyncUiState.Ready -> {
                StatusCard(
                    status = state.status,
                    remoteUri = state.remoteUri,
                    branch = state.branch
                )
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.remoteUri != null
                ) {
                    Text("Sync now")
                }
            }

            is SyncUiState.Syncing -> {
                StatusCard(
                    status = state.status,
                    remoteUri = null,
                    branch = state.status.branch.orEmpty()
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = progressLabel(state.step),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            is SyncUiState.Success -> {
                StatusCard(
                    status = state.status,
                    remoteUri = null,
                    branch = state.status.branch.orEmpty()
                )
                Text(
                    text = successMessage(state),
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh status")
                }
            }

            is SyncUiState.Error -> {
                state.status?.let { status ->
                    StatusCard(
                        status = status,
                        remoteUri = null,
                        branch = status.branch.orEmpty()
                    )
                }
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: SyncStatusSummary, remoteUri: String?, branch: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Branch", style = MaterialTheme.typography.labelMedium)
            Text(
                text = branch.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (remoteUri != null) {
                Text(text = "Remote", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = remoteUri,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(text = "Status", style = MaterialTheme.typography.labelMedium)
            Text(
                text = statusLabel(status),
                style = MaterialTheme.typography.titleMedium
            )
            if (status.message.isNotBlank()) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun statusLabel(status: SyncStatusSummary): String = when (status.state) {
    SyncStatusState.Clean -> "Clean"
    SyncStatusState.DirtyLocalChanges -> "Local changes"
    SyncStatusState.Ahead -> "Ahead (${status.aheadCount})"
    SyncStatusState.Behind -> "Behind (${status.behindCount})"
    SyncStatusState.Diverged -> "Diverged"
    SyncStatusState.ConflictRisk -> "Conflict risk"
    SyncStatusState.Unavailable -> "Unavailable"
    SyncStatusState.Error -> "Error"
}

private fun progressLabel(step: SyncProgressStep): String = when (step) {
    SyncProgressStep.ValidatingWorkspace -> "Validating workspace…"
    SyncProgressStep.ReadingCredentials -> "Reading credentials…"
    SyncProgressStep.InspectingStatus -> "Inspecting local changes…"
    SyncProgressStep.CommittingInboxChanges -> "Committing inbox changes…"
    SyncProgressStep.FetchingRemote -> "Fetching remote…"
    SyncProgressStep.IntegratingRemote -> "Integrating remote changes…"
    SyncProgressStep.PushingLocalCommits -> "Pushing local commits…"
    SyncProgressStep.RefreshingNotes -> "Refreshing notes…"
    SyncProgressStep.Complete -> "Complete"
}

private fun successMessage(state: SyncUiState.Success): String {
    val parts = mutableListOf("Sync completed.")
    if (state.committed) parts += "Inbox changes committed."
    if (state.pulled) parts += "Remote changes integrated."
    if (state.pushed) parts += "Local commits pushed."
    return parts.joinToString(" ")
}
