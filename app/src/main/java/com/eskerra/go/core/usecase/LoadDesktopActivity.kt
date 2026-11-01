package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.repository.DesktopActivityRepository
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
        val root = WorkspacePaths.resolve(filesDir, config.relativePath).getOrElse {
            return DesktopActivityStatus.Unavailable to null
        }
        val settings = loadVaultSettings(root).getOrElse {
            return DesktopActivityStatus.Unavailable to null
        }
        val r2 = settings.r2 ?: return DesktopActivityStatus.Inactive to null
        return try {
            DesktopActivityStatus.Inactive to repository.latestActivityAtEpochMillis(r2)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DesktopActivityStatus.Unavailable to null
        }
    }
}
