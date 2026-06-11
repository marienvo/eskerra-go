package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.LocalSettingsStore
import java.util.UUID

class EnsureDeviceInstanceId(private val store: LocalSettingsStore) {
    suspend operator fun invoke(): String {
        val current = store.load()
        if (current.deviceInstanceId.isNotEmpty()) return current.deviceInstanceId
        val newId = UUID.randomUUID().toString()
        store.save(current.copy(deviceInstanceId = newId))
        return newId
    }
}
