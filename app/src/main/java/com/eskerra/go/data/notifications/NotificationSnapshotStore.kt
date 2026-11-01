package com.eskerra.go.data.notifications

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventMatching
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.notifications.NotificationEvents
import org.json.JSONArray
import org.json.JSONObject

/** Transactionally stores the last complete scan and queues newly due occurrences. */
class NotificationSnapshotStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "notification-events.db", null, 2),
    NotificationSnapshotPersistence {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE scan_meta (workspace TEXT PRIMARY KEY, scanned_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE snapshot (workspace TEXT NOT NULL, event_key TEXT NOT NULL, note_id TEXT NOT NULL, type TEXT NOT NULL, due_at INTEGER NOT NULL, PRIMARY KEY(workspace, event_key))"
        )
        createReminderCache(db)
        db.execSQL(
            "CREATE TABLE pending (workspace TEXT NOT NULL, event_key TEXT NOT NULL, note_id TEXT NOT NULL, type TEXT NOT NULL, due_at INTEGER NOT NULL, PRIMARY KEY(workspace, event_key))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            createReminderCache(db)
        } else {
            error("Unsupported notification database upgrade: $oldVersion to $newVersion")
        }
    }

    override fun replaceSnapshot(workspace: String, events: List<NotificationEvent>, now: Long) {
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
            if (previous != null) migratePending(db, workspace, previous, events)
            db.delete("snapshot", "workspace = ?", arrayOf(workspace))
            for (event in events.distinctBy { it.key }) {
                db.insertOrThrow("snapshot", null, values(workspace, event))
            }
            db.execSQL(
                "DELETE FROM pending WHERE workspace = ? AND event_key NOT IN " +
                    "(SELECT event_key FROM snapshot WHERE workspace = ? AND due_at != 9223372036854775807)",
                arrayOf(workspace, workspace)
            )
            db.delete("pending", "workspace = ? AND due_at > ?", arrayOf(workspace, now.toString()))
            for (event in due) {
                insertIgnoringExisting(db, "pending", workspace, event)
            }
            val meta = ContentValues().apply {
                put("workspace", workspace)
                put("scanned_at", now)
            }
            check(
                db.insertWithOnConflict("scan_meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE) !=
                    -1L
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun cachedReminders(
        workspace: String,
        noteId: String,
        signature: String
    ): List<NotificationEvent>? {
        val json = readableDatabase.rawQuery(
            "SELECT payload FROM reminder_cache WHERE workspace = ? AND note_id = ? AND signature = ?",
            arrayOf(workspace, noteId, signature)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        val entries = JSONArray(json)
        return (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            NotificationEvent(
                item.getString("key"),
                noteId,
                NotificationEventType.REMINDER,
                item.getLong("due")
            )
        }
    }

    override fun cacheReminders(
        workspace: String,
        noteId: String,
        signature: String,
        events: List<NotificationEvent>
    ) {
        val json = JSONArray()
        for (event in events) {
            json.put(JSONObject().put("key", event.key).put("due", event.dueAtEpochMillis))
        }
        val values = ContentValues().apply {
            put("workspace", workspace)
            put("note_id", noteId)
            put("signature", signature)
            put("payload", json.toString())
        }
        check(
            writableDatabase.insertWithOnConflict(
                "reminder_cache",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            ) != -1L
        )
    }

    private fun createReminderCache(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reminder_cache (workspace TEXT NOT NULL, note_id TEXT NOT NULL, " +
                "signature TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(workspace, note_id))"
        )
    }

    /** Record local inbox writes before Git consumes the working-tree status. */
    fun rememberLocalInboxPaths(workspace: String, paths: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val initialized = db.rawQuery(
                "SELECT 1 FROM scan_meta WHERE workspace = ?",
                arrayOf(workspace)
            ).use { it.moveToFirst() }
            if (initialized) {
                for (path in paths) {
                    val event =
                        NotificationEvent("inbox:$path", path, NotificationEventType.INBOX, 0L)
                    insertIgnoringExisting(db, "snapshot", workspace, event)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearWorkspace(workspace: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("pending", "snapshot", "scan_meta", "reminder_cache")) {
                db.delete(table, "workspace = ?", arrayOf(workspace))
            }
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

    private fun migratePending(
        db: SQLiteDatabase,
        workspace: String,
        previous: Map<String, NotificationEvent>,
        current: List<NotificationEvent>
    ) {
        val matches = NotificationEventMatching.match(previous, current)
        val pendingEvents = readEvents(db, "pending", workspace)
        val pendingKeys = pendingEvents.keys
        val currentGroups = current.mapNotNull { event ->
            NotificationEventMatching.reminderGroup(event)?.let { it to event }
        }.groupBy({ it.first }, { it.second })
        for (event in current) {
            val group = NotificationEventMatching.reminderGroup(event)
            val fallback = if (group != null && currentGroups[group]?.size == 1) {
                pendingEvents.values.firstOrNull {
                    NotificationEventMatching.reminderGroup(it) ==
                        group
                }
            } else {
                null
            }
            val oldKey = (matches[event.key] ?: fallback)?.key ?: continue
            if (oldKey !in pendingKeys) continue
            if (oldKey == event.key) {
                val updated = ContentValues().apply { put("due_at", event.dueAtEpochMillis) }
                db.update(
                    "pending",
                    updated,
                    "workspace = ? AND event_key = ?",
                    arrayOf(workspace, oldKey)
                )
                continue
            }
            insertIgnoringExisting(db, "pending", workspace, event)
            db.delete("pending", "workspace = ? AND event_key = ?", arrayOf(workspace, oldKey))
        }
    }

    private fun insertIgnoringExisting(
        db: SQLiteDatabase,
        table: String,
        workspace: String,
        event: NotificationEvent
    ) {
        require(table == "snapshot" || table == "pending")
        db.execSQL(
            "INSERT OR IGNORE INTO $table (workspace, event_key, note_id, type, due_at) " +
                "VALUES (?, ?, ?, ?, ?)",
            arrayOf(workspace, event.key, event.noteId, event.type.name, event.dueAtEpochMillis)
        )
    }

    private fun values(workspace: String, event: NotificationEvent) = ContentValues().apply {
        put("workspace", workspace)
        put("event_key", event.key)
        put("note_id", event.noteId)
        put("type", event.type.name)
        put("due_at", event.dueAtEpochMillis)
    }
}
