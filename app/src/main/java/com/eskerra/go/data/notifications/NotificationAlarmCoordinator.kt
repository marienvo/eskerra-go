package com.eskerra.go.data.notifications

import android.content.Context
import android.os.SystemClock
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DatedNoteNotificationText
import com.eskerra.go.core.notifications.DatedNoteRecord
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
import com.eskerra.go.data.perf.ColdStartTrace
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
    private val isCurrentWorkspace: suspend (WorkspaceConfig) -> Boolean = { true },
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
        val initialState = loadState(workspace)
        val initialSchedules = DatedReminderPlanner.schedules(
            initialState.records,
            initialState.handled,
            reminderSettings.load(),
            now,
            localZone
        )
        val hasDue = initialState.pending.any { it.dueAtEpochMillis <= now } ||
            initialSchedules.any { it.dueAtEpochMillis <= now }
        val activity = if (hasDue) {
            loadDesktopActivity(config, filesDir)
        } else {
            DesktopActivityStatus.Inactive to null
        }
        if (!PostSyncNotificationRefresh.enabled(context) || !isCurrentWorkspace(config)) {
            scheduler.cancel()
            return
        }
        val state = loadState(workspace)
        val settings = reminderSettings.load()
        val schedules = DatedReminderPlanner.schedules(
            state.records,
            state.handled,
            settings,
            now,
            localZone
        )
        val decision = DesktopActivityGate.decide(
            state.pending,
            activity.first,
            activity.second,
            now
        )
        for (event in state.pending.filter { it.key in decision.skippedEventKeys }) {
            acknowledge(workspace, event)
            if (event.type == NotificationEventType.DATED_NOTE) {
                markHandled(workspace, event.noteId, today.toString())
            }
        }
        val deliverable = state.pending.filter { it.key in decision.deliverableEventKeys }
        for (event in deliverable.filter { it.type == NotificationEventType.DATED_NOTE }) {
            deliverFirstDated(config, workspace, event, today.toString())
        }
        val bundled = deliverable.filterNot { it.type == NotificationEventType.DATED_NOTE }
        if (bundled.isNotEmpty() && delivery.deliver(bundled)) {
            for (event in bundled) acknowledge(workspace, event)
        }
        if (activity.first != DesktopActivityStatus.Unavailable) {
            for (schedule in schedules.filter { it.dueAtEpochMillis <= now }) {
                deliverDaily(
                    config,
                    workspace,
                    schedule,
                    activity.second,
                    now,
                    today.toString()
                )
            }
        }
        val finalState = loadState(workspace)
        val nextDaily = DatedReminderPlanner.schedules(
            finalState.records,
            finalState.handled,
            reminderSettings.load(),
            now,
            localZone
        )
        val next = NotificationAlarmTiming.next(finalState.pending, nextDaily, now)
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }

    private suspend fun deliverFirstDated(
        config: WorkspaceConfig,
        workspace: String,
        event: NotificationEvent,
        day: String
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        gitSyncMutex.mutex.withLock {
            if (!canDeliver(config)) return@withLock
            val markdown = readLatestLocked(config, event.noteId) ?: return@withLock
            if (!NoteflowDatedNotes.isIncomplete(markdown)) {
                acknowledge(workspace, event)
                markHandled(workspace, event.noteId, day)
                return@withLock
            }
            val fresh = event.copy(
                message = DatedNoteNotificationText.resolve(event.noteId, markdown)
            )
            if (delivery.deliver(listOf(fresh))) {
                acknowledge(workspace, event)
                markHandled(workspace, event.noteId, day)
            }
        }
        ColdStartTrace.log(
            "notification.firstMutex",
            "took=${SystemClock.elapsedRealtime() - startedAt}ms"
        )
    }

    private suspend fun deliverDaily(
        config: WorkspaceConfig,
        workspace: String,
        schedule: DatedReminderSchedule,
        lastActivityAtEpochMillis: Long?,
        now: Long,
        day: String
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        gitSyncMutex.mutex.withLock {
            if (!canDeliver(config, requireRepetition = true)) return@withLock
            val state = loadState(workspace)
            val record = DatedReminderPlanner.latestAvailable(
                state.records,
                schedule.folder,
                java.time.LocalDate.parse(day)
            ) ?: return@withLock
            val handled = DatedReminderHandled(record.noteId, day)
            if (handled in state.handled) return@withLock
            if (state.pending.any {
                    it.type == NotificationEventType.DATED_NOTE && it.noteId == record.noteId
                }
            ) {
                markHandled(workspace, record.noteId, day)
                return@withLock
            }
            if (lastActivityAtEpochMillis?.let {
                    DesktopActivityGate.isActive(it, now)
                } == true
            ) {
                markHandled(workspace, record.noteId, day)
                return@withLock
            }
            val markdown = readLatestLocked(config, record.noteId) ?: return@withLock
            if (!NoteflowDatedNotes.isIncomplete(markdown)) {
                markHandled(workspace, record.noteId, day)
                return@withLock
            }
            val event = NotificationEvent(
                "dated-reminder:${record.folder}",
                record.noteId,
                NotificationEventType.DATED_REMINDER,
                schedule.dueAtEpochMillis,
                DatedNoteNotificationText.resolve(record.noteId, markdown)
            )
            if (delivery.deliver(listOf(event))) {
                markHandled(workspace, record.noteId, day)
            }
        }
        ColdStartTrace.log(
            "notification.dailyMutex",
            "took=${SystemClock.elapsedRealtime() - startedAt}ms"
        )
    }

    private suspend fun canDeliver(config: WorkspaceConfig, requireRepetition: Boolean = false) =
        PostSyncNotificationRefresh.enabled(context) &&
            isCurrentWorkspace(config) &&
            (!requireRepetition || reminderSettings.load().enabled)

    private suspend fun readLatestLocked(config: WorkspaceConfig, noteId: String): String? = try {
        notes.load(config, filesDir, NoteId(noteId)).getOrThrow().markdown
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun loadState(workspace: String): AlarmState = NotificationSnapshotStore(context).use {
        AlarmState(
            it.pending(workspace),
            it.datedNotes(workspace),
            it.datedReminderHandled(workspace)
        )
    }

    private fun acknowledge(workspace: String, event: NotificationEvent) {
        NotificationSnapshotStore(context).use {
            it.acknowledge(workspace, event.key, event.dueAtEpochMillis)
        }
    }

    private fun markHandled(workspace: String, noteId: String, day: String) {
        NotificationSnapshotStore(context).use {
            it.markDatedReminderHandled(workspace, noteId, day)
        }
    }

    private data class AlarmState(
        val pending: List<NotificationEvent>,
        val records: List<DatedNoteRecord>,
        val handled: Set<DatedReminderHandled>
    )

    private companion object {
        val RECONCILE_MUTEX = Mutex()
    }
}
