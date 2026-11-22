package com.eskerra.go.data.notifications

import android.content.Context
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DatedNoteNotificationText
import com.eskerra.go.core.notifications.DatedReminderHandled
import com.eskerra.go.core.notifications.DatedReminderPlanner
import com.eskerra.go.core.notifications.DatedReminderSchedule
import com.eskerra.go.core.notifications.DesktopActivityGate
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.notifications.NotificationAlarmTiming
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.repository.DatedReminderSettingsRepository
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.notes.FileNoteContentRepository
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationAlarmCoordinator(
    private val context: Context,
    private val filesDir: File,
    private val loadDesktopActivity: suspend (
        WorkspaceConfig,
        File
    ) -> Pair<DesktopActivityStatus, Long?>,
    private val scheduler: NotificationAlarmScheduling,
    private val gitSyncMutex: GitSyncMutex,
    private val notes: NoteContentRepository = FileNoteContentRepository(),
    private val reminderSettings: DatedReminderSettingsRepository =
        SharedPreferencesDatedReminderSettingsRepository(context),
    private val delivery: NotificationEventDelivery = NotificationDelivery(context),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    suspend fun reconcile(config: WorkspaceConfig) {
        RECONCILE_MUTEX.withLock { reconcileLocked(config) }
    }

    private suspend fun reconcileLocked(config: WorkspaceConfig) {
        if (!PostSyncNotificationRefresh.enabled(context)) {
            scheduler.cancel()
            return
        }
        val workspace = notificationWorkspaceKey(config)
        val now = clock()
        val localZone = zone()
        val today = java.time.Instant.ofEpochMilli(now).atZone(localZone).toLocalDate()
        val state = NotificationSnapshotStore(context).use { store ->
            AlarmState(
                store.pending(workspace),
                store.datedNotes(workspace),
                store.datedReminderHandled(workspace).toMutableSet()
            )
        }
        val settings = reminderSettings.load()
        val schedules = DatedReminderPlanner.schedules(
            state.records,
            state.handled,
            settings,
            now,
            localZone
        )
        val hasDue = state.pending.any { it.dueAtEpochMillis <= now } ||
            schedules.any { it.dueAtEpochMillis <= now }
        val activity = if (hasDue) {
            loadDesktopActivity(config, filesDir)
        } else {
            DesktopActivityStatus.Inactive to null
        }
        val decision = DesktopActivityGate.decide(
            state.pending,
            activity.first,
            activity.second,
            now
        )
        val acknowledged = mutableSetOf<String>()
        for (event in state.pending.filter { it.key in decision.skippedEventKeys }) {
            acknowledge(workspace, event)
            acknowledged += event.key
            if (event.type == NotificationEventType.DATED_NOTE) {
                markHandled(workspace, state.handled, event.noteId, today.toString())
            }
        }
        val deliverable = state.pending.filter { it.key in decision.deliverableEventKeys }
        for (batch in deliveryBatches(deliverable)) {
            if (!delivery.deliver(batch)) continue
            for (event in batch) {
                acknowledge(workspace, event)
                acknowledged += event.key
                if (event.type == NotificationEventType.DATED_NOTE) {
                    markHandled(workspace, state.handled, event.noteId, today.toString())
                }
            }
        }
        for (schedule in schedules.filter { it.dueAtEpochMillis <= now }) {
            deliverDaily(
                config,
                workspace,
                schedule,
                state,
                activity.first,
                activity.second,
                now,
                today.toString()
            )
        }
        val remaining = state.pending.filter { it.key !in acknowledged }
        val nextDaily = DatedReminderPlanner.schedules(
            state.records,
            state.handled,
            settings,
            now,
            localZone
        )
        val next = NotificationAlarmTiming.next(remaining, nextDaily, now)
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }

    private suspend fun deliverDaily(
        config: WorkspaceConfig,
        workspace: String,
        schedule: DatedReminderSchedule,
        state: AlarmState,
        desktopActivity: DesktopActivityStatus,
        lastActivityAtEpochMillis: Long?,
        now: Long,
        day: String
    ) {
        val today = java.time.LocalDate.parse(day)
        val record = DatedReminderPlanner.latestAvailable(state.records, schedule.folder, today)
            ?: return
        val handled = DatedReminderHandled(record.noteId, day)
        if (handled in state.handled) return
        if (state.pending.any {
                it.type == NotificationEventType.DATED_NOTE && it.noteId == record.noteId
            }
        ) {
            markHandled(workspace, state.handled, record.noteId, day)
            return
        }
        if (desktopActivity == DesktopActivityStatus.Unavailable) return
        if (lastActivityAtEpochMillis?.let {
                DesktopActivityGate.isActive(it, now)
            } == true
        ) {
            markHandled(workspace, state.handled, record.noteId, day)
            return
        }
        val markdown = readLatest(config, record.noteId) ?: return
        if (!NoteflowDatedNotes.isIncomplete(markdown)) {
            markHandled(workspace, state.handled, record.noteId, day)
            return
        }
        val event = NotificationEvent(
            "dated-reminder:${record.folder}",
            record.noteId,
            NotificationEventType.DATED_REMINDER,
            schedule.dueAtEpochMillis,
            DatedNoteNotificationText.resolve(record.noteId, markdown)
        )
        if (delivery.deliver(listOf(event))) {
            markHandled(workspace, state.handled, record.noteId, day)
        }
    }

    private suspend fun readLatest(config: WorkspaceConfig, noteId: String): String? =
        gitSyncMutex.mutex.withLock {
            try {
                notes.load(
                    config,
                    filesDir,
                    com.eskerra.go.core.model.NoteId(noteId)
                ).getOrThrow().markdown
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private fun acknowledge(workspace: String, event: NotificationEvent) {
        NotificationSnapshotStore(context).use {
            it.acknowledge(workspace, event.key, event.dueAtEpochMillis)
        }
    }

    private fun markHandled(
        workspace: String,
        handled: MutableSet<DatedReminderHandled>,
        noteId: String,
        day: String
    ) {
        NotificationSnapshotStore(context).use {
            it.markDatedReminderHandled(workspace, noteId, day)
        }
        handled += DatedReminderHandled(noteId, day)
    }

    private fun deliveryBatches(events: List<NotificationEvent>): List<List<NotificationEvent>> {
        val dated = events.filter {
            it.type == NotificationEventType.DATED_NOTE ||
                it.type == NotificationEventType.DATED_REMINDER
        }.map(::listOf)
        val bundled = events.filterNot {
            it.type == NotificationEventType.DATED_NOTE ||
                it.type == NotificationEventType.DATED_REMINDER
        }
        return if (bundled.isEmpty()) dated else dated + listOf(bundled)
    }

    private data class AlarmState(
        val pending: List<NotificationEvent>,
        val records: List<com.eskerra.go.core.notifications.DatedNoteRecord>,
        val handled: MutableSet<DatedReminderHandled>
    )

    private companion object {
        val RECONCILE_MUTEX = Mutex()
    }
}
