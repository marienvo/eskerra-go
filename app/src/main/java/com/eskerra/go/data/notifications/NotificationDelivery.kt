package com.eskerra.go.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eskerra.go.MainActivity
import com.eskerra.go.R
import com.eskerra.go.core.notifications.NotificationEvent

fun interface NotificationEventDelivery {
    fun deliver(events: List<NotificationEvent>): Boolean
}

class NotificationDelivery(private val context: Context) : NotificationEventDelivery {
    override fun deliver(events: List<NotificationEvent>): Boolean {
        if (events.isEmpty()) return true
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        createChannel()
        val noteIds = events.map { it.noteId }.distinct()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putStringArrayListExtra(EXTRA_NOTE_IDS, ArrayList(noteIds))
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (events.size == 1) title(events.single()) else "${events.size} notifications"
        val text = if (events.size == 1) label(events.single().noteId) else "Choose a note to open"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_brand_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (events.size > 1) {
            val style = NotificationCompat.InboxStyle()
            events.forEach { event -> style.addLine(label(event.noteId)) }
            builder.setStyle(style)
        }
        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vault notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun title(event: NotificationEvent) = when (event.type) {
        com.eskerra.go.core.notifications.NotificationEventType.INBOX -> "New inbox item"
        com.eskerra.go.core.notifications.NotificationEventType.SNOOZE -> "Snooze ended"
        com.eskerra.go.core.notifications.NotificationEventType.DATED_NOTE -> "New dated note"
        com.eskerra.go.core.notifications.NotificationEventType.REMINDER -> "Reminder"
    }

    private fun label(noteId: String) = noteId.substringAfterLast('/').removeSuffix(".md")

    companion object {
        const val EXTRA_NOTE_IDS = "com.eskerra.go.extra.NOTIFICATION_NOTE_IDS"
        private const val CHANNEL_ID = "vault_events"
        private const val NOTIFICATION_ID = 4200
        private const val REQUEST_CODE = 4200
    }
}
