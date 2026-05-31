package com.eskerra.go.app

/** Shell sync button presentation derived from app sync state. */
data class ShellSyncIndicatorState(
    val needsAttention: Boolean,
    val isChecking: Boolean,
    val isSyncing: Boolean,
    val badgeText: String?
)
