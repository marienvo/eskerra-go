package com.eskerra.go.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.data.workspace.WorkspaceSetupCompletion
import com.eskerra.go.data.workspace.WorkspaceStore
import com.eskerra.go.feature.setup.WorkspaceSetupScreen
import com.eskerra.go.ui.theme.EskerraGoTheme
import java.io.File

/**
 * Root gate: Loading, workspace setup, or the main app shell. Setup is not a
 * NavHost route.
 */
@Composable
fun AppRoot(
    workspaceStore: WorkspaceStore,
    setupCompletion: WorkspaceSetupCompletion,
    filesDir: File,
    loadInboxSummaries: LoadInboxSummaries,
    loadNoteForReading: LoadNoteForReading
) {
    EskerraGoTheme {
        val gateViewModel: AppGateViewModel = viewModel(
            factory = AppGateViewModel.factory(
                workspaceStore = workspaceStore,
                filesDir = filesDir
            )
        )
        val gateState by gateViewModel.gateState.collectAsState()

        when (val gate = gateState) {
            AppGateState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AppGateState.NeedsSetup -> {
                val activity = LocalContext.current as? ComponentActivity
                BackHandler {
                    activity?.finish()
                }

                val setupViewModel: WorkspaceSetupViewModel = viewModel(
                    factory = WorkspaceSetupViewModel.factory(
                        setupCompletion = setupCompletion,
                        filesDir = filesDir,
                        recoveryMessage = gate.recoveryMessage
                    )
                )
                val uiState = setupViewModel.uiState

                WorkspaceSetupScreen(
                    state = uiState,
                    onNameChange = setupViewModel::onNameChange,
                    onBranchChange = setupViewModel::onBranchChange,
                    onRemoteUriChange = setupViewModel::onRemoteUriChange,
                    onCredentialChange = setupViewModel::onCredentialChange,
                    onModeChange = setupViewModel::onModeChange,
                    onSubmit = {
                        setupViewModel.submit { config ->
                            gateViewModel.markReady(config)
                        }
                    }
                )
            }

            is AppGateState.Ready -> App(
                config = gate.config,
                filesDir = filesDir,
                loadInboxSummaries = loadInboxSummaries,
                loadNoteForReading = loadNoteForReading
            )
        }
    }
}
