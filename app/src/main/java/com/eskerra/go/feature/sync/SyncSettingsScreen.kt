package com.eskerra.go.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.shellScrollContentPadding

@Composable
fun SyncSettingsScreen(
    state: RemoteSyncSettingsUiState,
    onRemoteUriChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onReplacementTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(shellScrollContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Remote sync settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Store a sanitized HTTPS remote URL and branch. " +
                "Tokens stay in the credential store only.",
            style = MaterialTheme.typography.bodyMedium
        )

        when (state) {
            RemoteSyncSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading settings…",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            is RemoteSyncSettingsUiState.Ready -> {
                if (state.isConfigured) {
                    Text(text = "Current remote", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = state.displayedRemoteUri.orEmpty().ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Branch: ${state.displayedBranch.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (state.hasStoredCredential) {
                            "Access token is stored (not shown)."
                        } else {
                            "No access token stored."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Remote sync is not configured yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = state.editRemoteUri,
                    onValueChange = onRemoteUriChange,
                    label = { Text("Remote URL") },
                    singleLine = true,
                    enabled = !state.isSaving && !state.isTesting && !state.isClearing,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.editBranch,
                    onValueChange = onBranchChange,
                    label = { Text("Branch") },
                    singleLine = true,
                    enabled = !state.isSaving && !state.isTesting && !state.isClearing,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.editRemoteUri.trim().startsWith("https://", ignoreCase = true) &&
                    state.displayedRemoteUri?.trim()?.isNotEmpty() == true &&
                    state.editRemoteUri.trim() != state.displayedRemoteUri.trim()
                ) {
                    Text(
                        text = "Changing the remote URL requires a new access token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = state.replacementToken,
                    onValueChange = onReplacementTokenChange,
                    label = { Text("Access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !state.isSaving && !state.isTesting && !state.isClearing,
                    modifier = Modifier.fillMaxWidth()
                )

                state.statusMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving && !state.isTesting && !state.isClearing
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Save settings")
                }
                Button(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isConfigured &&
                        !state.isSaving &&
                        !state.isTesting &&
                        !state.isClearing
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Test connection")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isConfigured &&
                        !state.isSaving &&
                        !state.isTesting &&
                        !state.isClearing
                ) {
                    if (state.isClearing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Clear remote sync settings")
                }
            }
        }
    }
}
