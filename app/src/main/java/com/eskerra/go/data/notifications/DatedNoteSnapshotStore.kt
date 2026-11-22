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
                "message TEXT NOT NULL, PRIMARY KEY(workspace, note_id))"
        )
    }

    fun replace(db: SQLiteDatabase, workspace: String, events: List<NotificationEvent>, now: Long) {
        val firstSeen = read(db, workspace).associate { it.noteId to it.firstSeenAtEpochMillis }
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
                put("first_seen_at", firstSeen[event.noteId] ?: now)
                put("message", event.message.orEmpty())
            }
            check(db.insertOrThrow("dated_notes", null, values) != -1L)
        }
    }

    fun read(db: SQLiteDatabase, workspace: String): List<DatedNoteRecord> {
        val result = mutableListOf<DatedNoteRecord>()
        db.rawQuery(
            "SELECT note_id, folder, date, first_seen_at, message FROM dated_notes " +
                "WHERE workspace = ?",
            arrayOf(workspace)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += DatedNoteRecord(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getString(4)
                )
            }
        }
        return result
    }
}

fun NotificationSnapshotStore.datedNotes(workspace: String): List<DatedNoteRecord> =
    DatedNoteSnapshotStore.read(readableDatabase, workspace)
