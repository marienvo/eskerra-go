package com.eskerra.go.core.repository

import com.eskerra.go.core.model.R2Config

interface DesktopActivityRepository {
    suspend fun latestActivityAtEpochMillis(config: R2Config): Long?
}
