package com.eskerra.go.feature.search

import com.eskerra.go.core.search.VaultSearchNoteResult

sealed interface SearchUiState {
    data object Idle : SearchUiState

    data class Opening(
        val query: String,
        val previousResults: List<VaultSearchNoteResult> = emptyList()
    ) : SearchUiState

    data class Searching(
        val query: String,
        val previousResults: List<VaultSearchNoteResult> = emptyList()
    ) : SearchUiState

    data class Results(
        val query: String,
        val notes: List<VaultSearchNoteResult>,
        val indexReady: Boolean,
        val bodiesIndexReady: Boolean,
        val resultCountLabel: String
    ) : SearchUiState

    data class NoMatches(
        val query: String,
        val indexReady: Boolean,
        val bodiesIndexReady: Boolean
    ) : SearchUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = false,
        val detail: String? = null
    ) : SearchUiState
}
