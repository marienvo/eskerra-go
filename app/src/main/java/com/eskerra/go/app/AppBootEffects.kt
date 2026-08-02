package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ReconcileWorkspaceSyncBranch
import java.io.File

@Composable
internal fun AppBootEffects(
    config: WorkspaceConfig,
    filesDir: File,
    launchSettled: Boolean,
    reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch,
    appSyncViewModel: AppSyncViewModel,
    onConfigUpdated: (WorkspaceConfig) -> Unit,
    onConfigChanged: (WorkspaceConfig) -> Unit
) {
    // Keyed to the ViewModel: AppSyncViewModel is recreated when remoteUri/branch changes
    // (syncViewModelKey). A composition-lifetime flag would leave the new instance stuck in
    // SyncUiState.Loading with no automatic status advance.
    val bootSyncRequested = remember(appSyncViewModel) { mutableStateOf(false) }
    LaunchedEffect(launchSettled, appSyncViewModel) {
        if (!shouldTriggerBootSync(launchSettled, bootSyncRequested.value)) {
            return@LaunchedEffect
        }
        // launchSettled describes ready content; wait for that content to reach a frame before
        // starting Git/network work so sync stays off the first-render path.
        withFrameNanos { }
        bootSyncRequested.value = true
        appSyncViewModel.requestAutoSync()
    }

    LaunchedEffect(config) {
        val reconciled = reconcileWorkspaceConfig(
            config = config,
            filesDir = filesDir,
            reconcileWorkspaceSyncBranch = reconcileWorkspaceSyncBranch
        )
        if (reconciled != config) {
            onConfigChanged(reconciled)
            onConfigUpdated(reconciled)
        }
    }
}

@Composable
internal fun AppForegroundSyncEffect(appSyncViewModel: AppSyncViewModel) {
    DisposableEffect(appSyncViewModel) {
        // LifecycleRegistry may synchronously replay ON_START while addObserver runs to bring a
        // late subscriber up to the current state. That replay is not a real resume — boot sync
        // already covers cold start — so ignore events until addObserver returns.
        var accepting = false
        val observer = LifecycleEventObserver { _, event ->
            if (shouldAutoSyncOnLifecycleEvent(event, accepting)) {
                appSyncViewModel.requestAutoSync()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        accepting = true
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }
}

/** Auto-sync on ON_START only after the observer is fully registered ([accepting]). */
internal fun shouldAutoSyncOnLifecycleEvent(
    event: Lifecycle.Event,
    accepting: Boolean
): Boolean = accepting && event == Lifecycle.Event.ON_START

/**
 * Boot fires the auto-sync once per ViewModel instance, and only once launch has settled.
 * [alreadyRequested] is scoped to that instance (see remember(appSyncViewModel) in AppBootEffects).
 */
internal fun shouldTriggerBootSync(launchSettled: Boolean, alreadyRequested: Boolean): Boolean =
    launchSettled && !alreadyRequested
