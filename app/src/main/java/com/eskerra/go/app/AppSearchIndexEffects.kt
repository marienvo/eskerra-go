package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    maintainVaultSearchIndex: MaintainVaultSearchIndex
) {
    LaunchedEffect(config) {
        maintainVaultSearchIndex(config, filesDir)
    }
    LaunchedEffect(config) {
        while (true) {
            delay(RECONCILE_INTERVAL_MS)
            maintainVaultSearchIndex(config, filesDir)
        }
    }
}
