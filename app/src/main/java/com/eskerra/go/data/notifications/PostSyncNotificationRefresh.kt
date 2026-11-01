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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps snapshot failures separate from a successfully completed Git sync. */
object PostSyncNotificationRefresh {
    private const val SETTINGS = "notification_settings"
    private const val ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    suspend fun setEnabled(runtime: SyncRuntime, value: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val context = runtime.context ?: return@withContext Result.failure(
                IllegalStateException("Application context unavailable")
            )
            try {
                var changed = false
                var hasRemote = false
                runtime.gitSyncMutex.mutex.withLock {
                    val config = runtime.workspaceStore.read()
                    changed = enabled(context) != value
                    hasRemote = config?.remoteUri?.isNotBlank() == true
                    if (changed && config != null) {
                        NotificationSnapshotStore(context).use {
                            it.clearWorkspace(notificationWorkspaceKey(config))
                        }
                    }
                    check(
                        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                            .edit().putBoolean(ENABLED, value).commit()
                    )
                    if (changed &&
                        value &&
                        hasRemote
                    ) {
                        runtime.syncStateRepository.markGenerationRequested()
                    }
                }
                if (!value) NotificationAlarmScheduler(context).cancel()
                runtime.vaultSyncScheduler.reconcile()
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
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
                if (!enabled(context) || runtime.workspaceStore.read() != config) return
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
            runtime.notificationAlarmCoordinator?.reconcile(config)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("NotificationSnapshot", "Notification snapshot refresh failed")
        }
    }
}
