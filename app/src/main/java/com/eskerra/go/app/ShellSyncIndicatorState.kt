package com.eskerra.go.app

/**
 * Sync presentation derived from app sync state. [badgeText] drives the count/attention badge now
 * shown on the hamburger; [changeCount] is the numeric pending count for the menu's sync entry.
 * [spinning] is true while a sync is in flight (or within its minimum-visible hold window) and takes
 * precedence over [badgeText] in the shell: while spinning, the hamburger shows the rotating sync
 * glyph instead of the count/attention badge.
 */
data class ShellSyncIndicatorState(
    val badgeText: String?,
    val changeCount: Int?,
    val spinning: Boolean
)
