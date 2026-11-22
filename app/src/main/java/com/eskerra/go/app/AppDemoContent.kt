package com.eskerra.go.app

import com.eskerra.go.feature.menu.MenuEntry

/** Hamburger overflow menu entry ids (routed in [App]) and the builder for the entry list. */

internal const val MENU_SYNC_STATUS = "sync_status"
internal const val MENU_SYNC_SETTINGS = "sync_settings"
internal const val MENU_REMINDER_SETTINGS = "reminder_settings"

/**
 * Builds the hamburger menu for sync and reminder settings.
 */
internal fun buildMenuEntries(): List<MenuEntry> = listOf(
    MenuEntry(MENU_SYNC_STATUS, "Sync status"),
    MenuEntry(MENU_SYNC_SETTINGS, "Sync settings"),
    MenuEntry(MENU_REMINDER_SETTINGS, "Reminder settings")
)
