package com.eskerra.go.data.notifications

import android.content.Context
import android.util.Log
import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.sync.SyncRuntime
import com.eskerra.go.data.todayhub.FileNoteflowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/** Keeps snapshot failures separate from a successfully completed Git sync. */
object PostSyncNotificationRefresh {
    private const val SETTINGS = "notification_settings"
    private const val ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(
            SETTINGS,
            Context.MODE_PRIVATE
        ).edit().putBoolean(ENABLED, value).apply()
    }

    fun recordLocalChanges(context: Context, config: WorkspaceConfig, paths: Set<String>) {
        if (!enabled(context)) return
        val inboxPaths = paths.filter {
            InboxNotePath.isInboxRelativePath(it) && it.endsWith(".md")
        }.toSet()
        if (inboxPaths.isEmpty()) return
        try {
            NotificationSnapshotStore(context).use {
                it.rememberLocalInboxPaths(notificationWorkspaceKey(config), inboxPaths)
            }
        } catch (_: Exception) {
            // Fail closed for notifications if local-write identities cannot be preserved.
            val disabled = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, false).commit()
            check(disabled) { "Cannot disable notifications after snapshot failure" }
            Log.w("NotificationSnapshot", "Notifications disabled after snapshot failure")
        }
    }

    suspend fun run(runtime: SyncRuntime, config: WorkspaceConfig, result: SyncResult) {
        val context = runtime.context ?: return
        if (!result.registryRefreshed || !enabled(context)) return
        try {
            runtime.gitSyncMutex.mutex.withLock {
                if (runtime.workspaceStore.read() != config) return
                val registry = runtime.noteRegistryCache.refresh(
                    config,
                    runtime.filesDir
                ).getOrThrow()
                NotificationSnapshotStore(context).use { store ->
                    RefreshNotificationSnapshot(
                        FileNoteContentRepository(),
                        FileNoteflowRepository(),
                        store
                    ).refresh(config, runtime.filesDir, registry).getOrThrow()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("NotificationSnapshot", "Notification snapshot refresh failed")
        }
    }
}
