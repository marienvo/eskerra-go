package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.BootCacheStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
import com.eskerra.go.data.workspace.WorkspaceStore
import com.eskerra.go.data.workspace.resolveAppGateState
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stable app-start gate state, independent of Compose recomposition. */
class AppGateViewModel(
    private val workspaceStore: WorkspaceStore,
    private val bootCacheStore: BootCacheStore,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _gateState = MutableStateFlow<AppGateState>(AppGateState.Loading)
    val gateState: StateFlow<AppGateState> = _gateState.asStateFlow()

    private var backgroundValidationJob: Job? = null

    init {
        viewModelScope.launch {
            resolveGate()
        }
    }

    override fun onCleared() {
        backgroundValidationJob?.cancel()
        super.onCleared()
    }

    private suspend fun resolveGate() {
        val stored = workspaceStore.read() ?: run {
            _gateState.value = withContext(ioDispatcher) {
                resolveAppGateState(null, filesDir)
            }
            return
        }

        val computedFingerprint = withContext(ioDispatcher) {
            GateFingerprintComputer.compute(stored, filesDir)
        }
        val cachedFingerprint = bootCacheStore.readFingerprint()

        if (cachedFingerprint != null && cachedFingerprint == computedFingerprint) {
            _gateState.value = AppGateState.Ready(stored)
            startBackgroundValidation(stored)
            return
        }

        val localState = withContext(ioDispatcher) {
            resolveAppGateState(stored, filesDir)
        }
        _gateState.value = localState
        if (localState is AppGateState.Ready) {
            bootCacheStore.saveFingerprint(computedFingerprint)
            startBackgroundValidation(stored)
        } else {
            bootCacheStore.clearFingerprint()
        }
    }

    private fun startBackgroundValidation(stored: WorkspaceConfig) {
        backgroundValidationJob?.cancel()
        backgroundValidationJob = viewModelScope.launch {
            val validated = withContext(ioDispatcher) {
                resolveAppGateState(stored, filesDir)
            }
            when (validated) {
                is AppGateState.Ready -> {
                    val fingerprint = withContext(ioDispatcher) {
                        GateFingerprintComputer.compute(stored, filesDir)
                    }
                    bootCacheStore.saveFingerprint(fingerprint)
                }
                is AppGateState.NeedsSetup -> {
                    bootCacheStore.clearFingerprint()
                    if (_gateState.value is AppGateState.Ready) {
                        _gateState.value = validated
                    }
                }
                AppGateState.Loading -> Unit
            }
        }
    }

    /** Called only after workspace metadata has already been persisted. */
    fun markReady(config: WorkspaceConfig) {
        viewModelScope.launch {
            val fingerprint = withContext(ioDispatcher) {
                GateFingerprintComputer.compute(config, filesDir)
            }
            bootCacheStore.saveFingerprint(fingerprint)
            _gateState.value = AppGateState.Ready(config)
        }
    }

    /** Updates in-memory config after remote sync settings or branch reconciliation. */
    fun updateReadyConfig(config: WorkspaceConfig) {
        if (_gateState.value is AppGateState.Ready) {
            _gateState.value = AppGateState.Ready(config)
            viewModelScope.launch {
                val fingerprint = withContext(ioDispatcher) {
                    GateFingerprintComputer.compute(config, filesDir)
                }
                bootCacheStore.saveFingerprint(fingerprint)
            }
        }
    }

    companion object {
        fun factory(
            workspaceStore: WorkspaceStore,
            bootCacheStore: BootCacheStore,
            filesDir: File
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppGateViewModel(
                workspaceStore = workspaceStore,
                bootCacheStore = bootCacheStore,
                filesDir = filesDir
            ) as T
        }
    }
}
