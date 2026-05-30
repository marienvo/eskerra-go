package com.eskerra.go.feature.inbox

import com.eskerra.go.core.model.NoteSummary

/** Presentation state for the inbox list screen. */
sealed interface InboxUiState {
    data object Loading : InboxUiState

    data class Content(val notes: List<NoteSummary>) : InboxUiState

    data object Empty : InboxUiState

    data class Error(val message: String) : InboxUiState
}
