package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.repository.DesktopActivityRepository
import com.eskerra.go.core.repository.VaultSettingsRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadDesktopActivityTest {
    @Test
    fun incompleteR2ConfigurationAllowsDelivery() = runTest {
        val settings = EskerraSettings(
            r2 = R2Config(
                endpoint = "https://example.com",
                bucket = "bucket",
                accessKeyId = "",
                secretAccessKey = ""
            )
        )
        val settingsRepository = object : VaultSettingsRepository {
            override suspend fun loadShared(workspaceRoot: File) = Result.success(settings)
            override suspend fun saveShared(workspaceRoot: File, settings: EskerraSettings) =
                Result.success(Unit)
        }
        val activityRepository = object : DesktopActivityRepository {
            override suspend fun latestActivityAtEpochMillis(config: R2Config): Long? =
                error("R2 must not be called")
        }
        val load = LoadDesktopActivity(activityRepository, LoadVaultSettings(settingsRepository))

        val result = load(WorkspaceConfig("Notes", "workspace", null, "main", 1), File("."))

        assertEquals(DesktopActivityStatus.Inactive to null, result)
    }
}
