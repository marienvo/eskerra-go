package com.eskerra.go.core.repository

import com.eskerra.go.core.model.EskerraLocalSettings

interface LocalSettingsStore {
    suspend fun load(): EskerraLocalSettings
    suspend fun save(settings: EskerraLocalSettings)
}
