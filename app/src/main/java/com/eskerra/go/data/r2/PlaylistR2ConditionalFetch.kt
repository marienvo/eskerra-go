package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2ConditionalResult
import com.eskerra.go.core.usecase.LoadVaultSettings
import com.eskerra.go.core.vault.R2Settings
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistR2ConditionalFetch(
    private val loadVaultSettings: LoadVaultSettings,
    private val conditionalClient: R2PlaylistConditionalClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun fetch(workspaceRoot: File, etag: String?): R2ConditionalResult {
        val settings = loadVaultSettings(workspaceRoot).getOrNull()
            ?: return R2ConditionalResult.Missing
        val r2 = settings.r2?.takeIf { R2Settings.isVaultR2PlaylistConfigured(settings) }
            ?: return R2ConditionalResult.Missing
        return withContext(ioDispatcher) { conditionalClient.fetch(r2, etag) }
    }
}
