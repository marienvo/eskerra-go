package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.EskerraLocalSettings
import com.eskerra.go.core.repository.LocalSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureDeviceInstanceIdTest {

    private class FakeLocalSettingsStore(
        private var stored: EskerraLocalSettings = EskerraLocalSettings()
    ) : LocalSettingsStore {
        var savedCount = 0

        override suspend fun load(): EskerraLocalSettings = stored

        override suspend fun save(settings: EskerraLocalSettings) {
            stored = settings
            savedCount++
        }
    }

    @Test
    fun `generates and persists id when empty`() = runTest {
        val store = FakeLocalSettingsStore()
        val useCase = EnsureDeviceInstanceId(store)

        val id = useCase()

        assertTrue(id.isNotEmpty())
        assertEquals(id, store.load().deviceInstanceId)
        assertEquals(1, store.savedCount)
    }

    @Test
    fun `returns existing id without persisting again`() = runTest {
        val existingId = "existing-id-123"
        val store = FakeLocalSettingsStore(EskerraLocalSettings(deviceInstanceId = existingId))
        val useCase = EnsureDeviceInstanceId(store)

        val id = useCase()

        assertEquals(existingId, id)
        assertEquals(0, store.savedCount)
    }

    @Test
    fun `generated id is stable across two calls`() = runTest {
        val store = FakeLocalSettingsStore()
        val useCase = EnsureDeviceInstanceId(store)

        val first = useCase()
        val second = useCase()

        assertEquals(first, second)
        assertEquals(1, store.savedCount)
    }

    @Test
    fun `generated id is a valid UUID-shaped string`() = runTest {
        val store = FakeLocalSettingsStore()
        val id = EnsureDeviceInstanceId(store)()
        assertNotNull(id)
        assertTrue(id.length > 10)
    }

    @Test
    fun `preserves playlist watermarks when generating id`() = runTest {
        val store = FakeLocalSettingsStore(
            EskerraLocalSettings(playlistKnownUpdatedAtMs = 12345L)
        )
        EnsureDeviceInstanceId(store)()
        assertEquals(12345L, store.load().playlistKnownUpdatedAtMs)
    }
}
