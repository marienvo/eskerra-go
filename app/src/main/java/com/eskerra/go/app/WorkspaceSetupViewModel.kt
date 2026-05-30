package com.eskerra.go.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceSetupException
import com.eskerra.go.data.workspace.WorkspaceSetupMode
import com.eskerra.go.feature.setup.WorkspaceSetupUiState
import java.io.File
import kotlinx.coroutines.launch

class WorkspaceSetupViewModel(
    private val setupCompletion: WorkspaceSetupCompletion,
    private val filesDir: File,
    recoveryMessage: String?
) : ViewModel() {

    var uiState by mutableStateOf(WorkspaceSetupUiState(recoveryMessage = recoveryMessage))
        private set

    fun onNameChange(name: String) {
        uiState = uiState.copy(name = name, errorMessage = null)
    }

    fun onBranchChange(branch: String) {
        uiState = uiState.copy(branch = branch, errorMessage = null)
    }

    fun onRemoteUriChange(remoteUri: String) {
        uiState = uiState.copy(remoteUri = remoteUri, errorMessage = null)
    }

    fun onCredentialChange(credential: String) {
        uiState = uiState.copy(credential = credential, errorMessage = null)
    }

    fun onModeChange(mode: WorkspaceSetupMode) {
        uiState = uiState.copy(
            mode = mode,
            credential = if (mode == WorkspaceSetupMode.Clone) uiState.credential else "",
            errorMessage = null
        )
    }

    fun submit(onSuccess: (WorkspaceConfig) -> Unit) {
        if (uiState.isSubmitting) return

        val submission = uiState
        uiState = submission.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = setupCompletion.completeAndPersist(
                mode = submission.mode,
                name = submission.name,
                branch = if (submission.mode == WorkspaceSetupMode.Clone) submission.branch else "",
                remoteUri = submission.remoteUri.trim().ifBlank { null },
                credential = if (submission.mode == WorkspaceSetupMode.Clone) {
                    submission.credential.trim().ifBlank { null }
                } else {
                    null
                },
                filesDir = filesDir
            )
            result.fold(
                onSuccess = { config ->
                    uiState = uiState.copy(isSubmitting = false, credential = "")
                    onSuccess(config)
                },
                onFailure = { error ->
                    val message = when (error) {
                        is WorkspaceSetupException -> error.error.message()
                        else -> "Workspace setup failed"
                    }
                    uiState = uiState.copy(
                        isSubmitting = false,
                        errorMessage = message
                    )
                }
            )
        }
    }

    companion object {
        fun factory(
            setupCompletion: WorkspaceSetupCompletion,
            filesDir: File,
            recoveryMessage: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkspaceSetupViewModel(setupCompletion, filesDir, recoveryMessage) as T
        }
    }
}
