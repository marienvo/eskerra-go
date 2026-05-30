package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspaceStore
import com.eskerra.go.data.workspace.resolveAppGateState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Stable app-start gate state, independent of Compose recomposition. */
class AppGateViewModel(private val workspaceStore: WorkspaceStore, private val filesDir: File) :
    ViewModel() {

    private val _gateState = MutableStateFlow<AppGateState>(AppGateState.Loading)
    val gateState: StateFlow<AppGateState> = _gateState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshGate()
        }
    }

    private suspend fun refreshGate() {
        val config = workspaceStore.read()
        _gateState.value = resolveAppGateState(config, filesDir)
    }

    /** Called only after workspace metadata has already been persisted. */
    fun markReady(config: WorkspaceConfig) {
        _gateState.value = AppGateState.Ready(config)
    }

    companion object {
        fun factory(workspaceStore: WorkspaceStore, filesDir: File): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppGateViewModel(workspaceStore, filesDir) as T
            }
    }
}
