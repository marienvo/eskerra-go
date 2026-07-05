package com.eskerra.go.feature.sync

import com.eskerra.go.core.model.R2Jurisdiction

/** State for the vault R2 + this-device fields on the merged Sync settings screen. */
sealed interface VaultSettingsUiState {
    data object Loading : VaultSettingsUiState

    data class Ready(
        val r2Endpoint: String = "",
        val r2Jurisdiction: R2Jurisdiction = R2Jurisdiction.Default,
        val r2Bucket: String = "",
        val r2AccessKeyId: String = "",
        val r2SecretAccessKey: String = "",
        val displayName: String = "",
        val deviceName: String = "",
        val isSaving: Boolean = false,
        val statusMessage: String? = null,
        val errorMessage: String? = null
    ) : VaultSettingsUiState
}
