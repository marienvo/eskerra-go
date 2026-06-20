package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.AppShellMode
import com.eskerra.go.core.repository.LocalSettingsStore

class PersistAppShellMode(private val store: LocalSettingsStore) {
    suspend operator fun invoke(mode: AppShellMode) {
        val current = store.load()
        if (current.lastShellMode == mode) return
        store.save(current.copy(lastShellMode = mode))
    }
}
