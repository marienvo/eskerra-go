package com.eskerra.go.app

import androidx.compose.runtime.Composable
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.feature.todayhub.TodayHubPickerSheet
import com.eskerra.go.feature.todayhub.TodayHubUiState

internal fun TodayHubUiState?.canSwitchHub(): Boolean =
    (this as? TodayHubUiState.Content)?.showHubPicker == true

@Composable
internal fun AppHubPicker(
    visible: Boolean,
    state: TodayHubUiState?,
    onSelectHub: (NoteId) -> Unit,
    onDismiss: () -> Unit
) {
    val content = state as? TodayHubUiState.Content
    if (visible && content != null) {
        TodayHubPickerSheet(
            hubs = content.hubs,
            activeHubId = content.activeHubId,
            onPickHub = { picked ->
                onDismiss()
                onSelectHub(picked)
            },
            onDismiss = onDismiss
        )
    }
}
