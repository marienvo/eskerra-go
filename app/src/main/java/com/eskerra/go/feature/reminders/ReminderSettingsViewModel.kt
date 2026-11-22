package com.eskerra.go.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.repository.DatedReminderSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReminderSettingsUiState(
    val settings: DatedReminderSettings = DatedReminderSettings(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class ReminderSettingsViewModel(
    private val repository: DatedReminderSettingsRepository,
    private val onChanged: suspend () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderSettingsUiState(repository.load()))
    val uiState: StateFlow<ReminderSettingsUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null

    fun onEnabledChange(enabled: Boolean) {
        save(_uiState.value.settings.copy(enabled = enabled))
    }

    fun onTimeChange(hour: Int, minute: Int) {
        save(_uiState.value.settings.copy(minuteOfDay = hour * 60 + minute))
    }

    private fun save(settings: DatedReminderSettings) {
        saveJob?.cancel()
        val previous = _uiState.value.settings
        _uiState.update { it.copy(settings = settings, isSaving = true, errorMessage = null) }
        saveJob = viewModelScope.launch {
            val result = withContext(dispatcher) { repository.save(settings) }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        settings = previous,
                        isSaving = false,
                        errorMessage = "Could not save reminder settings."
                    )
                }
                return@launch
            }
            val rescheduleError = runCatching { onChanged() }.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    errorMessage = if (rescheduleError == null) {
                        null
                    } else {
                        "Settings saved, but alarms could not be rescheduled."
                    }
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: DatedReminderSettingsRepository,
            onChanged: suspend () -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReminderSettingsViewModel(repository, onChanged) as T
        }
    }
}
