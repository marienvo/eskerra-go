package com.eskerra.go.feature.sync

/** One downloaded binary row (already formatted for display). */
data class DownloadedBinaryRow(val relPath: String, val sizeLabel: String)

/** State for the "Downloaded binaries" tile on the Sync settings screen. */
data class BinariesUiState(
    val items: List<DownloadedBinaryRow> = emptyList(),
    val totalLabel: String = "0 B",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
