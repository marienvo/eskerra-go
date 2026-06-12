package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.repository.LocalSettingsStore

class LoadLocalSettings(private val store: LocalSettingsStore) {
    suspend operator fun invoke(): EskerraLocalSettings = store.load()
}
