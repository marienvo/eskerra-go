package com.eskerra.go.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.model.R2Jurisdiction

/**
 * Merged "Sync settings" screen: the downloaded-binaries tile, the vault R2 + device
 * fields, and the remote (Git) sync fields. Receives state and callbacks only.
 */
@Composable
fun SyncSettingsScreen(
    remoteState: RemoteSyncSettingsUiState,
    onRemoteUriChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onReplacementTokenChange: (String) -> Unit,
    onSaveRemote: () -> Unit,
    onTestConnection: () -> Unit,
    onClearRemote: () -> Unit,
    vaultState: VaultSettingsUiState,
    onR2EndpointChange: (String) -> Unit,
    onR2JurisdictionChange: (R2Jurisdiction) -> Unit,
    onR2BucketChange: (String) -> Unit,
    onR2AccessKeyIdChange: (String) -> Unit,
    onR2SecretAccessKeyChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSaveVault: () -> Unit,
    binariesState: BinariesUiState,
    onSyncBinaries: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(shellScrollContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Sync settings", style = MaterialTheme.typography.headlineMedium)

        BinariesTile(state = binariesState, onSyncNow = onSyncBinaries)

        Spacer(modifier = Modifier.height(4.dp))
        when (vaultState) {
            VaultSettingsUiState.Loading -> LoadingRow("Loading vault settings…")
            is VaultSettingsUiState.Ready -> R2AndDeviceSection(
                state = vaultState,
                onR2EndpointChange = onR2EndpointChange,
                onR2JurisdictionChange = onR2JurisdictionChange,
                onR2BucketChange = onR2BucketChange,
                onR2AccessKeyIdChange = onR2AccessKeyIdChange,
                onR2SecretAccessKeyChange = onR2SecretAccessKeyChange,
                onDisplayNameChange = onDisplayNameChange,
                onDeviceNameChange = onDeviceNameChange,
                onSave = onSaveVault
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        when (remoteState) {
            RemoteSyncSettingsUiState.Loading -> LoadingRow("Loading remote settings…")
            is RemoteSyncSettingsUiState.Ready -> RemoteSyncSection(
                state = remoteState,
                onRemoteUriChange = onRemoteUriChange,
                onBranchChange = onBranchChange,
                onReplacementTokenChange = onReplacementTokenChange,
                onSave = onSaveRemote,
                onTestConnection = onTestConnection,
                onClear = onClearRemote
            )
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
}
