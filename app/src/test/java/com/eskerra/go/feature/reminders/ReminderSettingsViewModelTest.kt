package com.eskerra.go.feature.reminders

import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.repository.DatedReminderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {
    @Test
    fun defaultsToEnabledAtSevenAndSavesToggleAndTime() = runViewModelTest {
        val repository = FakeRepository()
        var changes = 0
        val viewModel = ReminderSettingsViewModel(
            repository,
            onChanged = { changes++ },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        assertEquals(DatedReminderSettings(), viewModel.uiState.value.settings)
        viewModel.onEnabledChange(false)
        advanceUntilIdle()
        assertFalse(repository.saved.last().enabled)
        viewModel.onTimeChange(18, 15)
        advanceUntilIdle()
        assertEquals(18 * 60 + 15, repository.saved.last().minuteOfDay)
        assertEquals(2, changes)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun failedSaveRestoresPreviousSettingsWithoutRescheduling() = runViewModelTest {
        val repository = FakeRepository(fail = true)
        var changes = 0
        val viewModel = ReminderSettingsViewModel(
            repository,
            onChanged = { changes++ },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        viewModel.onEnabledChange(false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.settings.enabled)
        assertEquals(0, changes)
        assertEquals("Could not save reminder settings.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun savedSettingsRemainVisibleWhenReschedulingFails() = runViewModelTest {
        val repository = FakeRepository()
        val viewModel = ReminderSettingsViewModel(
            repository,
            onChanged = { error("alarm failed") },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        viewModel.onEnabledChange(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.settings.enabled)
        assertEquals(
            "Settings saved, but alarms could not be rescheduled.",
            viewModel.uiState.value.errorMessage
        )
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository(private val fail: Boolean = false) :
        DatedReminderSettingsRepository {
        val saved = mutableListOf<DatedReminderSettings>()
        override fun load() = DatedReminderSettings()
        override fun save(settings: DatedReminderSettings): Result<Unit> {
            if (fail) return Result.failure(IllegalStateException("read-only preferences"))
            saved += settings
            return Result.success(Unit)
        }
    }
}
