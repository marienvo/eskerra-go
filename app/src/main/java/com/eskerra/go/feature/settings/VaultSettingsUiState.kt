package com.eskerra.go.feature.settings

import com.eskerra.go.core.model.R2Jurisdiction

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
