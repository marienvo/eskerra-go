package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.EnsureDeviceInstanceId
import com.eskerra.go.core.usecase.LoadLocalSettings
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.usecase.SaveLocalSettings
import com.eskerra.go.core.usecase.SaveVaultSettings
import com.eskerra.go.core.vault.buildEskerraSettingsFromForm
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.sync.VaultSettingsUiState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VaultSettingsViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadVaultSettings: LoadVaultSettings,
    private val saveVaultSettings: SaveVaultSettings,
    private val loadLocalSettings: LoadLocalSettings,
    private val saveLocalSettings: SaveLocalSettings,
    private val ensureDeviceInstanceId: EnsureDeviceInstanceId
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<VaultSettingsUiState>(VaultSettingsUiState.Loading)
    val uiState: StateFlow<VaultSettingsUiState> = _uiState.asStateFlow()

    private var lastShared: EskerraSettings = EskerraSettings()

    init {
        refresh()
    }

    fun onR2EndpointChange(v: String) = updateReady { it.copy(r2Endpoint = v, errorMessage = null) }
    fun onR2JurisdictionChange(v: com.eskerra.go.core.model.R2Jurisdiction) =
        updateReady { it.copy(r2Jurisdiction = v, errorMessage = null) }
    fun onR2BucketChange(v: String) = updateReady { it.copy(r2Bucket = v, errorMessage = null) }
    fun onR2AccessKeyIdChange(v: String) =
        updateReady { it.copy(r2AccessKeyId = v, errorMessage = null) }
    fun onR2SecretAccessKeyChange(v: String) =
        updateReady { it.copy(r2SecretAccessKey = v, errorMessage = null) }
    fun onDisplayNameChange(v: String) = updateReady { it.copy(displayName = v) }
    fun onDeviceNameChange(v: String) = updateReady { it.copy(deviceName = v) }

    fun save() {
        val ready = currentReady() ?: return
        viewModelScope.launch {
            _uiState.value = ready.copy(isSaving = true, statusMessage = null, errorMessage = null)
            val workspaceRoot = workspaceRoot() ?: run {
                _uiState.value =
                    ready.copy(isSaving = false, errorMessage = "Workspace unavailable.")
                return@launch
            }

            val newSharedResult = buildEskerraSettingsFromForm(
                r2Endpoint = ready.r2Endpoint,
                r2Jurisdiction = ready.r2Jurisdiction,
                r2Bucket = ready.r2Bucket,
                r2AccessKeyId = ready.r2AccessKeyId,
                r2SecretAccessKey = ready.r2SecretAccessKey,
                previousShared = lastShared
            )
            val newShared = newSharedResult.getOrElse { error ->
                _uiState.value = ready.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Invalid settings."
                )
                return@launch
            }

            saveVaultSettings(workspaceRoot, newShared).onFailure { error ->
                _uiState.value = ready.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Failed to save settings."
                )
                return@launch
            }

            val local = loadLocalSettings()
            val localSaveResult = runCatching {
                saveLocalSettings(
                    local.copy(
                        displayName = ready.displayName.trim(),
                        deviceName = ready.deviceName.trim()
                    )
                )
            }
            if (localSaveResult.isFailure) {
                val error = localSaveResult.exceptionOrNull()
                _uiState.value = ready.copy(
                    isSaving = false,
                    errorMessage = error?.message ?: "Failed to save local settings."
                )
                return@launch
            }

            lastShared = newShared
            _uiState.value = readyFromShared(newShared, loadLocalSettings())
                .copy(statusMessage = "Settings saved.")
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = VaultSettingsUiState.Loading
            ensureDeviceInstanceId()
            val workspaceRoot = workspaceRoot()
            val shared =
                workspaceRoot?.let { loadVaultSettings(it).getOrNull() } ?: EskerraSettings()
            lastShared = shared
            val local = loadLocalSettings()
            _uiState.value = readyFromShared(shared, local)
        }
    }

    private fun readyFromShared(
        shared: EskerraSettings,
        local: com.eskerra.go.core.model.EskerraLocalSettings
    ): VaultSettingsUiState.Ready = VaultSettingsUiState.Ready(
        r2Endpoint = shared.r2?.endpoint.orEmpty(),
        r2Jurisdiction = shared.r2?.jurisdiction
            ?: com.eskerra.go.core.model.R2Jurisdiction.Default,
        r2Bucket = shared.r2?.bucket.orEmpty(),
        r2AccessKeyId = shared.r2?.accessKeyId.orEmpty(),
        r2SecretAccessKey = shared.r2?.secretAccessKey.orEmpty(),
        displayName = local.displayName,
        deviceName = local.deviceName
    )

    private fun workspaceRoot(): File? =
        WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()

    private fun currentReady(): VaultSettingsUiState.Ready? =
        _uiState.value as? VaultSettingsUiState.Ready

    private fun updateReady(transform: (VaultSettingsUiState.Ready) -> VaultSettingsUiState.Ready) {
        val ready = currentReady() ?: return
        _uiState.value = transform(ready)
    }

    companion object {
        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadVaultSettings: LoadVaultSettings,
            saveVaultSettings: SaveVaultSettings,
            loadLocalSettings: LoadLocalSettings,
            saveLocalSettings: SaveLocalSettings,
            ensureDeviceInstanceId: EnsureDeviceInstanceId
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VaultSettingsViewModel(
                config = config,
                filesDir = filesDir,
                loadVaultSettings = loadVaultSettings,
                saveVaultSettings = saveVaultSettings,
                loadLocalSettings = loadLocalSettings,
                saveLocalSettings = saveLocalSettings,
                ensureDeviceInstanceId = ensureDeviceInstanceId
            ) as T
        }
    }
}
