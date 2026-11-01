package com.eskerra.go.core.usecase

import android.util.Log
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.repository.DesktopActivityRepository
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CancellationException

class LoadDesktopActivity(
    private val repository: DesktopActivityRepository,
    private val loadVaultSettings: LoadVaultSettings
) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Pair<DesktopActivityStatus, Long?> {
        val root = WorkspacePaths.resolve(filesDir, config.relativePath).getOrElse { error ->
            Log.w(TAG, "Cannot resolve workspace for desktop activity", error)
            return DesktopActivityStatus.Unavailable to null
        }
        val settings = loadVaultSettings(root).getOrElse { error ->
            Log.w(TAG, "Cannot load settings for desktop activity", error)
            return DesktopActivityStatus.Unavailable to null
        }
        if (!R2Settings.isVaultR2Configured(settings)) {
            return DesktopActivityStatus.Inactive to null
        }
        val r2 = checkNotNull(settings.r2)
        return try {
            DesktopActivityStatus.Inactive to repository.latestActivityAtEpochMillis(r2)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Desktop activity request failed", error)
            DesktopActivityStatus.Unavailable to null
        }
    }

    private companion object {
        const val TAG = "DesktopActivity"
    }
}
