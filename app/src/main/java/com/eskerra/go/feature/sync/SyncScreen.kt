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
import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.SyncAttemptOutcome
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncRecoveryAction
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
    onOpenSettings: () -> Unit,
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
                    branch = state.branch,
                    checkedOutBranch = state.status.branch
                )
                PreflightCard(preflight = state.preflight)
                DiagnosticCard(diagnostic = state.diagnostic)
                if (state.remoteUri == null) {
                    Text(
                        text = "Configure remote sync before syncing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.remoteUri != null && state.preflight.canSync
                ) {
                    Text("Sync now")
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remote sync settings")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh status")
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
                state.warningMessage?.let { warning ->
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh status")
                }
            }

            is SyncUiState.Error -> {
                state.status?.let { status ->
                    StatusCard(
                        status = status,
                        remoteUri = null,
                        branch = status.branch.orEmpty(),
                        checkedOutBranch = status.branch
                    )
                }
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error
                )
                RecoveryHint(recoveryAction = state.recoveryAction)
                Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh status")
                }
                if (state.recoveryAction.suggestOpenSettings) {
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open sync settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreflightCard(preflight: SyncPreflightSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Preflight", style = MaterialTheme.typography.labelMedium)
            Text(
                text = preflight.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (preflight.canSync) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticCard(diagnostic: SafeSyncDiagnostic) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Diagnostics", style = MaterialTheme.typography.labelMedium)
            diagnostic.sanitizedRemote?.let { remote ->
                Text(
                    text = "Remote: $remote",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            diagnostic.branch?.let { branch ->
                Text(
                    text = "Branch: $branch",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "Inbox changes: ${diagnostic.inboxChangeCount}, " +
                    "other changes: ${diagnostic.nonInboxChangeCount}",
                style = MaterialTheme.typography.bodySmall
            )
            if (diagnostic.aheadCount > 0 || diagnostic.behindCount > 0) {
                Text(
                    text = "Ahead: ${diagnostic.aheadCount}, behind: ${diagnostic.behindCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            diagnostic.lastSync?.let { last ->
                Text(
                    text = "Last sync: ${lastSyncOutcomeLabel(last.outcome)}" +
                        last.errorCategory?.let { " ($it)" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecoveryHint(recoveryAction: SyncRecoveryAction) {
    Text(
        text = recoveryAction.hint,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (recoveryAction.localNotesAvailable) {
        Text(
            text = "Local notes remain available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusCard(
    status: SyncStatusSummary,
    remoteUri: String?,
    branch: String,
    checkedOutBranch: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Branch", style = MaterialTheme.typography.labelMedium)
            val mismatch = checkedOutBranch != null &&
                branch.isNotBlank() &&
                checkedOutBranch.isNotBlank() &&
                branch != checkedOutBranch
            Text(
                text = branch.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = if (mismatch) 4.dp else 8.dp)
            )
            if (mismatch) {
                Text(
                    text = "Checked out on \"$checkedOutBranch\". Sync uses \"$branch\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
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

private fun lastSyncOutcomeLabel(outcome: SyncAttemptOutcome): String = when (outcome) {
    SyncAttemptOutcome.Success -> "Success"
    SyncAttemptOutcome.PartialSuccess -> "Partial success"
    SyncAttemptOutcome.Failed -> "Failed"
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
