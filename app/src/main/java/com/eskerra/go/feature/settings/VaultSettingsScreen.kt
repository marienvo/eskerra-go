package com.eskerra.go.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.model.R2Jurisdiction

private val AccentBlue = Color(0xFF3B82F6)
private val MutedText = Color(0xFFCFCFCF)
private val ChipSelectedBorder = AccentBlue
private val ChipUnselectedBorder = Color(0x1FFFFFFF)
private val ChipSelectedFill = Color(0x263B82F6)

@Composable
fun VaultSettingsScreen(
    state: VaultSettingsUiState,
    onR2EndpointChange: (String) -> Unit,
    onR2JurisdictionChange: (R2Jurisdiction) -> Unit,
    onR2BucketChange: (String) -> Unit,
    onR2AccessKeyIdChange: (String) -> Unit,
    onR2SecretAccessKeyChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(shellScrollContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        when (state) {
            VaultSettingsUiState.Loading -> {
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

            is VaultSettingsUiState.Ready -> ReadyContent(
                state = state,
                onR2EndpointChange = onR2EndpointChange,
                onR2JurisdictionChange = onR2JurisdictionChange,
                onR2BucketChange = onR2BucketChange,
                onR2AccessKeyIdChange = onR2AccessKeyIdChange,
                onR2SecretAccessKeyChange = onR2SecretAccessKeyChange,
                onDisplayNameChange = onDisplayNameChange,
                onDeviceNameChange = onDeviceNameChange,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun ReadyContent(
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

    SectionTitle("Vault (synced)")
    Text(
        text = "Stored in .eskerra/settings-shared.json",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText
    )

    Spacer(modifier = Modifier.height(4.dp))

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
        placeholder = { Text("Access key ID") },
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
        placeholder = { Text("Secret access key") },
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

    Text(
        text = "R2 credentials are stored as plain JSON in the vault folder. " +
            "Acceptable for private vaults. Future server-side auth is planned.",
        style = MaterialTheme.typography.bodySmall,
        color = MutedText
    )

    state.statusMessage?.let { Text(text = it, color = AccentBlue) }
    state.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text("Save changes")
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
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
                color = if (isSelected) ChipSelectedFill else Color.Transparent,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) ChipSelectedBorder else ChipUnselectedBorder
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
