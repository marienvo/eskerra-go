package com.eskerra.go.data.notifications

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.notifications.NotificationEvents

/** Transactionally stores the last complete scan and queues newly due occurrences. */
class NotificationSnapshotStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "notification-events.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE scan_meta (workspace TEXT PRIMARY KEY, scanned_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE snapshot (workspace TEXT NOT NULL, event_key TEXT NOT NULL, note_id TEXT NOT NULL, type TEXT NOT NULL, due_at INTEGER NOT NULL, PRIMARY KEY(workspace, event_key))"
        )
        db.execSQL(
            "CREATE TABLE pending (workspace TEXT NOT NULL, event_key TEXT NOT NULL, note_id TEXT NOT NULL, type TEXT NOT NULL, due_at INTEGER NOT NULL, PRIMARY KEY(workspace, event_key))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported notification database upgrade: $oldVersion to $newVersion")
    }

    fun replaceSnapshot(workspace: String, events: List<NotificationEvent>, now: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val lastScan = db.rawQuery(
                "SELECT scanned_at FROM scan_meta WHERE workspace = ?",
                arrayOf(workspace)
            )
                .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            val previous = if (lastScan == null) null else readSnapshot(db, workspace)
            val due = NotificationEvents.newlyDue(previous, lastScan ?: 0L, events, now)
            db.delete("snapshot", "workspace = ?", arrayOf(workspace))
            for (event in events.distinctBy { it.key }) {
                db.insertOrThrow("snapshot", null, values(workspace, event))
            }
            db.execSQL(
                "DELETE FROM pending WHERE workspace = ? AND event_key NOT IN " +
                    "(SELECT event_key FROM snapshot WHERE workspace = ?)",
                arrayOf(workspace, workspace)
            )
            for (event in due) {
                db.insertWithOnConflict(
                    "pending",
                    null,
                    values(workspace, event),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            val meta = ContentValues().apply {
                put("workspace", workspace)
                put("scanned_at", now)
            }
            db.insertWithOnConflict("scan_meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun pending(workspace: String): List<NotificationEvent> = readEvents(
        readableDatabase,
        "pending",
        workspace
    ).values.toList()

    fun acknowledge(workspace: String, eventKey: String) {
        writableDatabase.delete(
            "pending",
            "workspace = ? AND event_key = ?",
            arrayOf(workspace, eventKey)
        )
    }

    private fun readSnapshot(
        db: SQLiteDatabase,
        workspace: String
    ): Map<String, NotificationEvent> = readEvents(db, "snapshot", workspace)

    private fun readEvents(
        db: SQLiteDatabase,
        table: String,
        workspace: String
    ): Map<String, NotificationEvent> {
        require(table == "snapshot" || table == "pending")
        val result = mutableMapOf<String, NotificationEvent>()
        db.rawQuery(
            "SELECT event_key, note_id, type, due_at FROM $table WHERE workspace = ?",
            arrayOf(workspace)
        )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val event =
                        NotificationEvent(
                            cursor.getString(0),
                            cursor.getString(1),
                            NotificationEventType.valueOf(cursor.getString(2)),
                            cursor.getLong(3)
                        )
                    result[event.key] = event
                }
            }
        return result
    }

    private fun values(workspace: String, event: NotificationEvent) = ContentValues().apply {
        put("workspace", workspace)
        put("event_key", event.key)
        put("note_id", event.noteId)
        put("type", event.type.name)
        put("due_at", event.dueAtEpochMillis)
    }
}
