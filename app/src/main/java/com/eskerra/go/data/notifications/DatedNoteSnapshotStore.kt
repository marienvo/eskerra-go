package com.eskerra.go.data.notifications

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.eskerra.go.core.notifications.DatedNoteRecord
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType

internal object DatedNoteSnapshotStore {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE dated_notes (workspace TEXT NOT NULL, note_id TEXT NOT NULL, " +
                "folder TEXT NOT NULL, date TEXT NOT NULL, first_seen_at INTEGER NOT NULL, " +
                "message TEXT NOT NULL, first_available_at INTEGER, " +
                "PRIMARY KEY(workspace, note_id))"
        )
        db.execSQL("CREATE TABLE dated_baseline (workspace TEXT PRIMARY KEY)")
    }

    fun hasBaseline(db: SQLiteDatabase, workspace: String): Boolean = db.rawQuery(
        "SELECT 1 FROM dated_baseline WHERE workspace = ?",
        arrayOf(workspace)
    ).use { it.moveToFirst() }

    fun replace(db: SQLiteDatabase, workspace: String, events: List<NotificationEvent>, now: Long) {
        val previous = read(db, workspace).associateBy { it.noteId }
        db.delete("dated_notes", "workspace = ?", arrayOf(workspace))
        events.filter {
            it.type == NotificationEventType.DATED_NOTE ||
                it.type == NotificationEventType.FUTURE_DATED_NOTE
        }.distinctBy { it.noteId }.forEach { event ->
            val values = ContentValues().apply {
                put("workspace", workspace)
                put("note_id", event.noteId)
                put("folder", event.noteId.substringBeforeLast('/'))
                put("date", event.noteId.substringAfterLast('/').removeSuffix(".md"))
                put("first_seen_at", previous[event.noteId]?.firstSeenAtEpochMillis ?: now)
                put("message", event.message.orEmpty())
                val firstAvailable = previous[event.noteId]?.firstAvailableAtEpochMillis
                if (event.type == NotificationEventType.DATED_NOTE || firstAvailable != null) {
                    put("first_available_at", firstAvailable ?: now)
                }
            }
            check(db.insertOrThrow("dated_notes", null, values) != -1L)
        }
        val baseline = ContentValues().apply { put("workspace", workspace) }
        check(
            db.insertWithOnConflict(
                "dated_baseline",
                null,
                baseline,
                SQLiteDatabase.CONFLICT_REPLACE
            ) != -1L
        )
    }

    fun read(db: SQLiteDatabase, workspace: String): List<DatedNoteRecord> {
        val result = mutableListOf<DatedNoteRecord>()
        db.rawQuery(
            "SELECT note_id, folder, date, first_seen_at, message, first_available_at " +
                "FROM dated_notes " +
                "WHERE workspace = ?",
            arrayOf(workspace)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += DatedNoteRecord(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getString(4),
                    if (cursor.isNull(5)) null else cursor.getLong(5)
                )
            }
        }
        return result
    }
}

fun NotificationSnapshotStore.datedNotes(workspace: String): List<DatedNoteRecord> =
    DatedNoteSnapshotStore.read(readableDatabase, workspace)

fun NotificationSnapshotStore.resetDatedRegistration(workspace: String) {
    writableDatabase.beginTransaction()
    try {
        writableDatabase.delete("dated_notes", "workspace = ?", arrayOf(workspace))
        writableDatabase.delete("dated_baseline", "workspace = ?", arrayOf(workspace))
        writableDatabase.setTransactionSuccessful()
    } finally {
        writableDatabase.endTransaction()
    }
}
