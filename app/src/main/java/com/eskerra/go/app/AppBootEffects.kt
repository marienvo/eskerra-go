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
    val bootSyncRequested = remember { mutableStateOf(false) }
    LaunchedEffect(launchSettled) {
        if (!launchSettled || bootSyncRequested.value) {
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
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            val shouldAutoSync = shouldAutoSyncOnLifecycleEvent(event, firstStart)
            if (event == Lifecycle.Event.ON_START) {
                firstStart = false
            }
            if (shouldAutoSync) {
                appSyncViewModel.requestAutoSync()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }
}

internal fun shouldAutoSyncOnLifecycleEvent(
    event: Lifecycle.Event,
    isFirstStart: Boolean
): Boolean = event == Lifecycle.Event.ON_START && !isFirstStart
