package com.eskerra.go.feature.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eskerra.go.data.workspace.WorkspaceSetupMode

/**
 * Stateless workspace setup screen. Receives all state and reports user actions
 * through callbacks.
 */
@Composable
fun WorkspaceSetupScreen(
    state: WorkspaceSetupUiState,
    onNameChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onRemoteUriChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
    onModeChange: (WorkspaceSetupMode) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Set up workspace",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Create your single notes workspace under app-private storage.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        state.recoveryMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        SetupModeOption(
            label = "Initialize new local workspace",
            selected = state.mode == WorkspaceSetupMode.InitializeLocal,
            onSelect = { onModeChange(WorkspaceSetupMode.InitializeLocal) }
        )
        SetupModeOption(
            label = "Clone from remote",
            selected = state.mode == WorkspaceSetupMode.Clone,
            onSelect = { onModeChange(WorkspaceSetupMode.Clone) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Workspace name") },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.mode == WorkspaceSetupMode.Clone) {
            OutlinedTextField(
                value = state.branch,
                onValueChange = onBranchChange,
                label = { Text("Branch") },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.remoteUri,
                onValueChange = onRemoteUriChange,
                label = { Text("Remote URI") },
                supportingText = {
                    Text("Use file:// for local bare repos or https:// for hosted remotes.")
                },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.credential,
                onValueChange = onCredentialChange,
                label = { Text("Access token") },
                supportingText = {
                    Text(
                        "Required for HTTPS remotes. Stored separately via CredentialStore."
                    )
                },
                singleLine = true,
                enabled = !state.isSubmitting,
                visualTransformation = WorkspaceSetupInputOptions.credentialVisualTransformation,
                keyboardOptions = WorkspaceSetupInputOptions.credentialKeyboardOptions,
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = onSubmit,
                enabled = state.name.isNotBlank() &&
                    (
                        state.mode != WorkspaceSetupMode.Clone ||
                            (
                                state.branch.isNotBlank() &&
                                    state.remoteUri.isNotBlank() &&
                                    (
                                        !state.remoteUri.trim()
                                            .startsWith("https://", ignoreCase = true) ||
                                            state.credential.isNotBlank()
                                        )
                                )
                        ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}

/** Password-style input defaults for the optional access token field. */
internal object WorkspaceSetupInputOptions {
    val credentialVisualTransformation = PasswordVisualTransformation()
    val credentialKeyboardOptions = KeyboardOptions(
        autoCorrectEnabled = false,
        keyboardType = KeyboardType.Password
    )
}

@Composable
private fun SetupModeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
