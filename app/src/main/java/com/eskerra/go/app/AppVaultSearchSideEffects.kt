package com.eskerra.go.app

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.TouchVaultSearchPaths
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.touchVaultSearchPathsAsync(
    touchVaultSearchPaths: TouchVaultSearchPaths,
    config: WorkspaceConfig,
    filesDir: File,
    paths: List<String>
) {
    if (paths.isEmpty()) return
    launch {
        touchVaultSearchPaths(config, filesDir, paths)
    }
}
