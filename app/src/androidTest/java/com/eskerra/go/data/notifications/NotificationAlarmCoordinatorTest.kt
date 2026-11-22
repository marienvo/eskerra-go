package com.eskerra.go.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.notifications.DesktopActivityGate
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.repository.DatedReminderSettingsRepository
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.git.GitSyncMutex
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
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

    private class Fixture(
        markdown: Map<String, String>,
        private val activity: Pair<DesktopActivityStatus, Long?> =
            DesktopActivityStatus.Inactive to null,
        private val deliverResult: Boolean = true,
        private val onDesktopCheck: () -> Unit = {},
        private val currentTime: () -> Long = { NOW }
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = WorkspaceConfig(
            "vault",
            "vault",
            "https://example.com",
            "main",
            UUID.randomUUID().mostSignificantBits
        )
        val workspace = notificationWorkspaceKey(config)
        val scheduler = FakeScheduler()
        val deliveries = mutableListOf<List<NotificationEvent>>()
        val deliveriesInsideMutex = mutableListOf<Boolean>()
        val reads = mutableListOf<String>()
        val desktopChecksOutsideMutex = mutableListOf<Boolean>()
        val noteReadsInsideMutex = mutableListOf<Boolean>()
        private val mutex = GitSyncMutex()
        private val notes = object : NoteContentRepository {
            override suspend fun load(
                config: WorkspaceConfig,
                filesDir: File,
                noteId: NoteId
            ): Result<NoteContent> {
                reads += noteId.value
                noteReadsInsideMutex += mutex.mutex.isLocked
                return Result.success(
                    NoteContent(
                        noteId,
                        NotePath.fromRelativePath(noteId.value).getOrThrow(),
                        markdown.getValue(noteId.value)
                    )
                )
            }
        }
        var repetitionEnabled = true
        private val settings = object : DatedReminderSettingsRepository {
            override fun load() = DatedReminderSettings(enabled = repetitionEnabled)
            override fun save(settings: DatedReminderSettings) = Result.success(Unit)
        }
        val coordinator = newCoordinator()

        init {
            context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", true).commit()
        }

        fun newCoordinator() = NotificationAlarmCoordinator(
            context = context,
            filesDir = File("."),
            loadDesktopActivity = { _, _ ->
                desktopChecksOutsideMutex += !mutex.mutex.isLocked
                onDesktopCheck()
                activity
            },
            scheduler = scheduler,
            gitSyncMutex = mutex,
            notes = notes,
            reminderSettings = settings,
            delivery = { events ->
                deliveries += events
                deliveriesInsideMutex += mutex.mutex.isLocked
                deliverResult
            },
            clock = currentTime,
            zone = { ZONE }
        )

        fun seed(events: List<NotificationEvent>) {
            NotificationSnapshotStore(context).use {
                it.replaceSnapshot(workspace, events, FIRST_SEEN)
            }
        }

        fun handled() = NotificationSnapshotStore(context).use {
            it.datedReminderHandled(workspace)
        }

        fun pending() = NotificationSnapshotStore(context).use { it.pending(workspace) }

        fun handledKey(noteId: String) =
            com.eskerra.go.core.notifications.DatedReminderHandled(noteId, "2026-11-22")
    }

    private class FakeScheduler : NotificationAlarmScheduling {
        var next: Long? = null
        override fun schedule(atEpochMillis: Long) {
            next = atEpochMillis
        }

        override fun cancel() {
            next = null
        }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val NOW = at("2026-11-22T08:00:00")
        val FIRST_SEEN = at("2026-11-01T08:00:00")
        const val OLDER = "Flow/Work/2026-11-21.md"
        const val NEWEST = "Flow/Work/2026-11-22.md"

        fun at(value: String): Long = LocalDateTime.parse(value).atZone(ZONE)
            .toInstant().toEpochMilli()

        fun event(
            noteId: String,
            due: Long = 0,
            type: NotificationEventType = NotificationEventType.DATED_NOTE
        ) = NotificationEvent("dated:$noteId", noteId, type, due, "Reminder")
    }
}
