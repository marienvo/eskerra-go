package com.eskerra.go.feature.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.model.R2Jurisdiction

internal val AccentBlue = Color(0xFF3B82F6)
private val MutedText = Color(0xFFCFCFCF)

@Composable
internal fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
}

/** Cloudflare R2 credentials + this-device fields, with its own Save action. */
@Composable
internal fun R2AndDeviceSection(
    state: VaultSettingsUiState.Ready,
    onR2EndpointChange: (String) -> Unit,
    onR2JurisdictionChange: (R2Jurisdiction) -> Unit,
    onR2BucketChange: (String) -> Unit,
    onR2AccessKeyIdChange: (String) -> Unit,
    onR2SecretAccessKeyChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val busy = state.isSaving

    SectionTitle("Cloudflare R2 (optional)")
    Text(
        text = "Leave all empty to clear R2. Values come from vault JSON.",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText
    )
    OutlinedTextField(
        value = state.r2Endpoint,
        onValueChange = onR2EndpointChange,
        label = { Text("Endpoint URL") },
        placeholder = { Text("https://accountid.r2.cloudflarestorage.com") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    JurisdictionChips(
        selected = state.r2Jurisdiction,
        enabled = !busy,
        onSelect = onR2JurisdictionChange
    )
    OutlinedTextField(
        value = state.r2Bucket,
        onValueChange = onR2BucketChange,
        label = { Text("Bucket") },
        placeholder = { Text("Bucket name") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.r2AccessKeyId,
        onValueChange = onR2AccessKeyIdChange,
        label = { Text("Access key ID") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.r2SecretAccessKey,
        onValueChange = onR2SecretAccessKeyChange,
        label = { Text("Secret access key") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(4.dp))
    SectionTitle("This device")
    Text(
        text = ".eskerra/settings-local.json — not synced with Git by default.",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText
    )
    OutlinedTextField(
        value = state.displayName,
        onValueChange = onDisplayNameChange,
        label = { Text("Display name") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.deviceName,
        onValueChange = onDeviceNameChange,
        label = { Text("Device name") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )

    state.statusMessage?.let { Text(text = it, color = AccentBlue) }
    state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

    Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text("Save R2 & device")
    }
}

/** Remote (Git) sync URL / branch / token, with Save, Test, and Clear actions. */
@Composable
internal fun RemoteSyncSection(
    state: RemoteSyncSettingsUiState.Ready,
    onRemoteUriChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onReplacementTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onClear: () -> Unit
) {
    val busy = state.isSaving || state.isTesting || state.isClearing

    SectionTitle("Remote sync (Git)")
    Text(
        text = "Sanitized HTTPS remote URL and branch. Tokens stay in the credential store only.",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText
    )
    if (state.isConfigured) {
        Text(
            text = "Current: ${state.displayedRemoteUri.orEmpty().ifBlank { "—" }} " +
                "(branch ${state.displayedBranch.ifBlank { "—" }})",
            style = MaterialTheme.typography.bodySmall
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
            style = MaterialTheme.typography.bodySmall
        )
    }
    OutlinedTextField(
        value = state.editRemoteUri,
        onValueChange = onRemoteUriChange,
        label = { Text("Remote URL") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.editBranch,
        onValueChange = onBranchChange,
        label = { Text("Branch") },
        singleLine = true,
        enabled = !busy,
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
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    )
    state.statusMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
    state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text("Save settings")
    }
    Button(
        onClick = onTestConnection,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.isConfigured && !busy
    ) {
        if (state.isTesting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text("Test connection")
    }
    OutlinedButton(
        onClick = onClear,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.isConfigured && !busy
    ) {
        if (state.isClearing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text("Clear remote sync settings")
    }
}

@Composable
private fun JurisdictionChips(
    selected: R2Jurisdiction,
    enabled: Boolean,
    onSelect: (R2Jurisdiction) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        R2Jurisdiction.entries.forEach { jurisdiction ->
            val isSelected = jurisdiction == selected
            Surface(
                onClick = { if (enabled) onSelect(jurisdiction) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) Color(0x263B82F6) else Color.Transparent,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) AccentBlue else Color(0x1FFFFFFF)
                )
            ) {
                Text(
                    text = jurisdiction.label(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) AccentBlue else MutedText
                )
            }
        }
    }
}

private fun R2Jurisdiction.label(): String = when (this) {
    R2Jurisdiction.Default -> "Default"
    R2Jurisdiction.Eu -> "EU"
    R2Jurisdiction.Fedramp -> "FedRAMP"
}
