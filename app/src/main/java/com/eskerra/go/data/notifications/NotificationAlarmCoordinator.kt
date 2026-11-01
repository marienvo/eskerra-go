package com.eskerra.go.data.notifications

import android.content.Context
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DesktopActivityGate
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.usecase.LoadDesktopActivity
import java.io.File

class NotificationAlarmCoordinator(
    private val context: Context,
    private val filesDir: File,
    private val loadDesktopActivity: LoadDesktopActivity,
    private val scheduler: NotificationAlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun reconcile(config: WorkspaceConfig) {
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
                for (key in decision.skippedEventKeys) store.acknowledge(workspace, key)
            }
        }
        val next = decision.nextAlarmAtEpochMillis
        if (next == null) scheduler.cancel() else scheduler.schedule(next)
    }
}
