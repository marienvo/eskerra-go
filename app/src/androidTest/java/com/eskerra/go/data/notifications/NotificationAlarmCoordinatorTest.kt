package com.eskerra.go.data.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.notifications.DesktopActivityGate
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationAlarmCoordinatorTest {
    @Test
    fun newestCompletedNoteStopsReminderWithoutReadingOlderNotes() = runTest {
        val fixture = Fixture(markdown = mapOf(NEWEST to "# ✅ Done", OLDER to "# Open"))
        fixture.seed(listOf(event(OLDER), event(NEWEST, Long.MAX_VALUE)))
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(listOf(NEWEST), fixture.reads)
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.handled().contains(fixture.handledKey(NEWEST)))
    }

    @Test
    fun incompleteLatestNoteUsesFreshFrontmatterAndMutexBoundaries() = runTest {
        val markdown = "---\nnotification: Fresh reminder\n---\n# Open"
        val fixture = Fixture(markdown = mapOf(NEWEST to markdown))
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(listOf(NEWEST), fixture.reads)
        assertEquals("Fresh reminder", fixture.deliveries.flatten().single().message)
        assertEquals(
            NotificationEventType.DATED_REMINDER,
            fixture.deliveries.flatten().single().type
        )
        assertTrue(fixture.desktopChecksOutsideMutex.all { it })
        assertTrue(fixture.noteReadsInsideMutex.all { it })
        assertTrue(fixture.deliveriesInsideMutex.all { it })
    }

    @Test
    fun activeDesktopSkipsDayWithoutReadingNote() = runTest {
        val fixture = Fixture(
            markdown = mapOf(NEWEST to "# Open"),
            activity = DesktopActivityStatus.Inactive to NOW - 1
        )
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.reads.isEmpty())
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.handled().contains(fixture.handledKey(NEWEST)))
    }

    @Test
    fun deadlineCrossingBeforeFirstDueCheckStillAppliesDesktopFilter() = runTest {
        val deadline = at("2026-11-22T07:00:00")
        var clockReads = 0
        val fixture = Fixture(
            markdown = mapOf(OLDER to "# Open"),
            activity = DesktopActivityStatus.Inactive to deadline - 1,
            currentTime = {
                if (clockReads++ == 0) deadline - 1_000 else deadline
            }
        )
        fixture.seed(listOf(event(OLDER)))
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(listOf(true), fixture.desktopChecksOutsideMutex)
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.reads.isEmpty())
        assertTrue(fixture.handled().contains(fixture.handledKey(OLDER)))
        assertEquals(at("2026-11-23T07:00:00"), fixture.scheduler.next)
    }

    @Test
    fun workspaceChangeDuringLateDesktopCheckKeepsOldBundlePending() = runTest {
        val deadline = at("2026-11-22T07:00:00")
        var clockReads = 0
        var current = true
        val fixture = Fixture(
            markdown = emptyMap(),
            onDesktopCheck = { current = false },
            currentTime = {
                if (clockReads++ == 0) deadline - 1_000 else deadline
            },
            workspaceCurrent = { current }
        )
        val bundled = NotificationEvent(
            "reminder:old-workspace",
            "Inbox/old.md",
            NotificationEventType.REMINDER,
            deadline
        )
        fixture.seed(listOf(bundled))
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(listOf(true), fixture.desktopChecksOutsideMutex)
        assertTrue(fixture.deliveries.isEmpty())
        assertEquals(listOf(bundled), fixture.pending())
        assertEquals(null, fixture.scheduler.next)
    }

    @Test
    fun temporaryDesktopFailureRetriesSameDay() = runTest {
        val fixture = Fixture(
            markdown = mapOf(NEWEST to "# Open"),
            activity = DesktopActivityStatus.Unavailable to null
        )
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.handled().isEmpty())
        assertEquals(NOW + DesktopActivityGate.RETRY_DELAY_MILLIS, fixture.scheduler.next)
    }

    @Test
    fun disablingRepetitionDuringDesktopCheckPreventsStaleDelivery() = runTest {
        lateinit var fixture: Fixture
        fixture = Fixture(
            markdown = mapOf(NEWEST to "# Open"),
            onDesktopCheck = { fixture.repetitionEnabled = false }
        )
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.reads.isEmpty())
        assertTrue(fixture.handled().isEmpty())
    }

    @Test
    fun newerNoteDuringDesktopCheckGetsFirstNotificationInsteadOfStaleReminder() = runTest {
        lateinit var fixture: Fixture
        fixture = Fixture(
            markdown = mapOf(OLDER to "# Open", NEWEST to "# Open"),
            onDesktopCheck = {
                NotificationSnapshotStore(fixture.context).use { store ->
                    store.replaceSnapshot(
                        fixture.workspace,
                        listOf(event(OLDER), event(NEWEST)),
                        NOW
                    )
                }
            }
        )
        fixture.seed(listOf(event(OLDER)))
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(listOf(NEWEST), fixture.deliveries.flatten().map { it.noteId })
        assertEquals(
            listOf(NotificationEventType.DATED_NOTE),
            fixture.deliveries.flatten().map { it.type }
        )
        assertEquals(listOf(NEWEST), fixture.reads)
    }

    @Test
    fun desktopCheckAcrossMidnightLetsPreviousDayExpire() = runTest {
        var currentTime = at("2026-11-22T23:59:00")
        val fixture = Fixture(
            markdown = mapOf(OLDER to "# Open"),
            onDesktopCheck = { currentTime = at("2026-11-23T00:01:00") },
            currentTime = { currentTime }
        )
        fixture.seed(listOf(event(OLDER)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.reads.isEmpty())
        assertTrue(fixture.handled().isEmpty())
        assertEquals(at("2026-11-23T07:00:00"), fixture.scheduler.next)
    }

    @Test
    fun reminderTimeChangeAfterPlanningIsRecheckedInsideGitMutex() = runTest {
        val seven = DatedReminderSettings(minuteOfDay = 7 * 60)
        val nine = seven.copy(minuteOfDay = 9 * 60)
        val fixture = Fixture(
            markdown = mapOf(OLDER to "# Open"),
            settingsSequence = listOf(seven, seven, nine)
        )
        fixture.seed(listOf(event(OLDER)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.deliveries.isEmpty())
        assertTrue(fixture.reads.isEmpty())
        assertEquals(at("2026-11-22T09:00:00"), fixture.scheduler.next)
    }

    @Test
    fun missingPermissionLeavesReminderForRetry() = runTest {
        val fixture = Fixture(markdown = mapOf(NEWEST to "# Open"), deliverResult = false)
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.handled().isEmpty())
        assertEquals(NOW + DesktopActivityGate.RETRY_DELAY_MILLIS, fixture.scheduler.next)
    }

    @Test
    fun delayedFirstNotificationWinsOverDailyReminder() = runTest {
        val fixture = Fixture(markdown = mapOf(OLDER to "# Open"))
        NotificationSnapshotStore(fixture.context).use { store ->
            store.replaceSnapshot(
                fixture.workspace,
                listOf(event(OLDER, Long.MAX_VALUE, NotificationEventType.FUTURE_DATED_NOTE)),
                FIRST_SEEN
            )
            store.replaceSnapshot(
                fixture.workspace,
                listOf(event(OLDER)),
                at("2026-11-21T08:00:00")
            )
        }
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(
            listOf(NotificationEventType.DATED_NOTE),
            fixture.deliveries.flatten().map {
                it.type
            }
        )
        assertEquals(listOf(OLDER), fixture.reads)
        assertTrue(fixture.handled().contains(fixture.handledKey(OLDER)))
    }

    @Test
    fun delayedFirstNotificationStopsWhenNoteWasCheckedLocally() = runTest {
        val fixture = Fixture(markdown = mapOf(OLDER to "# ✅ Done"))
        NotificationSnapshotStore(fixture.context).use { store ->
            store.replaceSnapshot(
                fixture.workspace,
                listOf(event(OLDER, Long.MAX_VALUE, NotificationEventType.FUTURE_DATED_NOTE)),
                FIRST_SEEN
            )
            store.replaceSnapshot(
                fixture.workspace,
                listOf(event(OLDER)),
                at("2026-11-21T08:00:00")
            )
        }
        fixture.coordinator.reconcile(fixture.config)
        assertTrue(fixture.deliveries.isEmpty())
        assertEquals(listOf(OLDER), fixture.reads)
        assertTrue(fixture.pending().isEmpty())
    }

    @Test
    fun restartedCoordinatorDoesNotDeliverSameDayTwice() = runTest {
        val fixture = Fixture(markdown = mapOf(NEWEST to "# Open"))
        fixture.seed(listOf(event(NEWEST)))
        fixture.coordinator.reconcile(fixture.config)
        fixture.newCoordinator().reconcile(fixture.config)
        assertEquals(1, fixture.deliveries.flatten().size)
        assertEquals(1, fixture.reads.size)
    }

    @Test
    fun simultaneousFirstNotificationsAreDeliveredSeparately() = runTest {
        val other = "Flow/Home/2026-11-22.md"
        val fixture = Fixture(markdown = mapOf(NEWEST to "# Work", other to "# Home"))
        NotificationSnapshotStore(fixture.context).use { store ->
            store.replaceSnapshot(fixture.workspace, emptyList(), FIRST_SEEN)
            store.replaceSnapshot(fixture.workspace, listOf(event(NEWEST), event(other)), NOW - 1)
        }
        fixture.coordinator.reconcile(fixture.config)
        assertEquals(2, fixture.deliveries.size)
        assertTrue(fixture.deliveries.all { it.size == 1 })
        assertEquals(setOf(NEWEST, other), fixture.deliveries.flatten().map { it.noteId }.toSet())
    }
}
