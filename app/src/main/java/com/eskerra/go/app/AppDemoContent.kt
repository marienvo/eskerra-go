package com.eskerra.go.app

import com.eskerra.go.feature.menu.MenuEntry

/** Hamburger overflow menu entry ids (routed in [App]) and the builder for the entry list. */

internal const val MENU_SYNC_NOW = "sync_now"
internal const val MENU_SYNC_SETTINGS = "sync_settings"
internal const val MENU_SETTINGS = "settings"
internal const val MENU_WORKSPACES = "workspaces"
internal const val MENU_ABOUT = "about"

/**
 * Builds the hamburger menu. The "Sync …" trigger only appears when a remote is configured; its
 * label carries the pending change count (or "Sync now" when nothing is known — remote may still
 * hold changes we haven't seen). It is never disabled.
 */
internal fun buildMenuEntries(syncChangeCount: Int?, remoteConfigured: Boolean): List<MenuEntry> =
    buildList {
        if (remoteConfigured) {
            add(MenuEntry(MENU_SYNC_NOW, syncNowLabel(syncChangeCount)))
        }
        add(MenuEntry(MENU_SYNC_SETTINGS, "Sync settings"))
        add(MenuEntry(MENU_SETTINGS, "Settings"))
        add(MenuEntry(MENU_WORKSPACES, "Workspaces"))
        add(MenuEntry(MENU_ABOUT, "About"))
    }

private fun syncNowLabel(changeCount: Int?): String = when {
    changeCount == null || changeCount <= 0 -> "Sync now"
    changeCount == 1 -> "Sync 1 change"
    else -> "Sync $changeCount changes"
}
