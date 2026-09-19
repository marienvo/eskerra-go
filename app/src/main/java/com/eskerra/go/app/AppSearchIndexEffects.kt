package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.MaintainVaultSearchIndex
import java.io.File
import kotlinx.coroutines.delay

private const val RECONCILE_INTERVAL_MS = 5 * 60 * 1000L

/** Foreground-only vault search index warm + periodic reconcile (spec §12.1). */
@Composable
internal fun AppSearchIndexEffects(
    config: WorkspaceConfig,
    filesDir: File,
    launchSettled: Boolean,
    maintainVaultSearchIndex: MaintainVaultSearchIndex
) {
    LaunchedEffect(config, launchSettled) {
        if (!shouldMaintainSearchIndex(launchSettled)) {
            return@LaunchedEffect
        }
        // The indexer opens SQLite and walks the vault, so it must start after content's first
        // rendered frame rather than competing with registry restore during cold start.
        withFrameNanos { }
        maintainVaultSearchIndex(config, filesDir)
        while (true) {
            delay(RECONCILE_INTERVAL_MS)
            maintainVaultSearchIndex(config, filesDir)
        }
    }
}

internal fun shouldMaintainSearchIndex(launchSettled: Boolean): Boolean = launchSettled
