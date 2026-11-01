package com.eskerra.go.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eskerra.go.data.sync.SyncRuntimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationAlarmScheduler.ACTION_ALARM &&
            intent.action !in RESCHEDULE_ACTIONS
        ) {
            return
        }
        val runtime = (context.applicationContext as? SyncRuntimeProvider)?.syncRuntime ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runtime.workspaceStore.read()?.let {
                    runtime.notificationAlarmCoordinator?.reconcile(it)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
