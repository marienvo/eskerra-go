package com.eskerra.go.app

import com.eskerra.go.feature.menu.MenuEntry

/** Hamburger overflow menu entry ids (routed in [App]) and the builder for the entry list. */

internal const val MENU_SYNC_STATUS = "sync_status"
internal const val MENU_SYNC_SETTINGS = "sync_settings"

/**
 * Builds the hamburger menu containing only "Sync status" and "Sync settings".
 */
internal fun buildMenuEntries(): List<MenuEntry> = listOf(
    MenuEntry(MENU_SYNC_STATUS, "Sync status"),
    MenuEntry(MENU_SYNC_SETTINGS, "Sync settings")
)
