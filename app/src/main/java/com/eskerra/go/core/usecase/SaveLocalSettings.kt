package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.repository.LocalSettingsStore

class SaveLocalSettings(private val store: LocalSettingsStore) {
    suspend operator fun invoke(settings: EskerraLocalSettings) = store.save(settings)
}
