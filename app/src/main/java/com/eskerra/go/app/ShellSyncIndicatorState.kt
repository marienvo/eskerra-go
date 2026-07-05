package com.eskerra.go.app

/**
 * Sync presentation derived from app sync state. [badgeText] drives the count/attention badge now
 * shown on the hamburger; [changeCount] is the numeric pending count for the menu's sync entry.
 */
data class ShellSyncIndicatorState(
    val badgeText: String?,
    val changeCount: Int?
)
