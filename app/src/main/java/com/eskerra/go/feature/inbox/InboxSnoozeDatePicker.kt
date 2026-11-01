package com.eskerra.go.feature.inbox

import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Date picker millis are UTC calendar dates, not the eventual snooze instant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxSnoozeDatePicker(
    today: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val earliestDate = today.plusDays(1)
    val earliestMillis = remember(earliestDate) {
        earliestDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val selectable = remember(earliestMillis, earliestDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= earliestMillis

            override fun isSelectableYear(year: Int): Boolean = year >= earliestDate.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = earliestMillis,
        selectableDates = selectable
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("Snooze") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, title = { Text("Snooze until 9:00 AM") })
    }
}

@Composable
internal fun InboxSnoozeButton(enabled: Boolean, onDateSelected: (LocalDate) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.IconButton(onClick = { open = true }, enabled = enabled) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.Schedule,
            contentDescription = "Snooze selected notes"
        )
    }
    if (open) {
        InboxSnoozeDatePicker(
            today = LocalDate.now(),
            onDismiss = { open = false },
            onDateSelected = { date ->
                open = false
                onDateSelected(date)
            }
        )
    }
}
