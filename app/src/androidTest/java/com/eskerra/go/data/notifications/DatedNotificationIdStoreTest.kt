package com.eskerra.go.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatedNotificationIdStoreTest {
    @Test
    fun collidingHashesGetDistinctPersistentIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = "Flow/Aa/2026-11-22.md"
        val second = "Flow/BB/2026-11-22.md"
        assertEquals(first.hashCode(), second.hashCode())
        val store = DatedNotificationIdStore(context)
        val firstId = store.idFor(first)
        val secondId = store.idFor(second)
        assertNotEquals(firstId, secondId)
        assertEquals(firstId, DatedNotificationIdStore(context).idFor(first))
        assertEquals(secondId, DatedNotificationIdStore(context).idFor(second))
    }
}
