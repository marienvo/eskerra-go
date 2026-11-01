package com.eskerra.go.data.notifications

import android.content.Context
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DesktopActivityGate
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.usecase.LoadDesktopActivity
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationAlarmCoordinator(
    private val context: Context,
    private val filesDir: File,
    private val loadDesktopActivity: LoadDesktopActivity,
    private val scheduler: NotificationAlarmScheduler,
    private val delivery: NotificationEventDelivery = NotificationDelivery(context),
    private val clock: () -> Long = System::currentTimeMillis
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
        val pending = NotificationSnapshotStore(context).use { it.pending(workspace) }
        if (pending.isEmpty()) {
            scheduler.cancel()
            return
        }
        val now = clock()
        val hasDue = pending.any { it.dueAtEpochMillis <= now }
        val activity = if (hasDue) {
            loadDesktopActivity(config, filesDir)
        } else {
            DesktopActivityStatus.Inactive to null
        }
        val decision = DesktopActivityGate.decide(pending, activity.first, activity.second, now)
        if (decision.skippedEventKeys.isNotEmpty()) {
            NotificationSnapshotStore(context).use { store ->
                pending.filter { it.key in decision.skippedEventKeys }.forEach {
                    store.acknowledge(workspace, it.key, it.dueAtEpochMillis)
                }
            }
        }
        val deliverable = pending.filter { it.key in decision.deliverableEventKeys }
        if (deliverable.isNotEmpty() && delivery.deliver(deliverable)) {
            NotificationSnapshotStore(context).use { store ->
                for (event in deliverable) {
                    store.acknowledge(workspace, event.key, event.dueAtEpochMillis)
                }
            }
        }
        val next = decision.nextAlarmAtEpochMillis
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }

    private companion object {
        val RECONCILE_MUTEX = Mutex()
    }
}
