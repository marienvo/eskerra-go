package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildMenuEntriesTest {

    @Test
    fun menuContainsSyncAndReminderSettings() {
        val entries = buildMenuEntries()
        assertEquals(
            listOf("Sync status", "Sync settings", "Reminder settings"),
            entries.map { it.label }
        )
    }

    @Test
    fun menuEntryIdsMatchExpectedRoutes() {
        val entries = buildMenuEntries()
        assertEquals(MENU_SYNC_STATUS, entries[0].id)
        assertEquals(MENU_SYNC_SETTINGS, entries[1].id)
        assertEquals(MENU_REMINDER_SETTINGS, entries[2].id)
    }
}
