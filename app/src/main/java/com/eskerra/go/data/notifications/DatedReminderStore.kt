package com.eskerra.go.data.notifications

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.eskerra.go.core.notifications.DatedReminderHandled

internal object DatedReminderStore {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE dated_handled (workspace TEXT NOT NULL, note_id TEXT NOT NULL, " +
                "day TEXT NOT NULL, PRIMARY KEY(workspace, note_id, day))"
        )
    }

    fun read(db: SQLiteDatabase, workspace: String): Set<DatedReminderHandled> {
        val result = mutableSetOf<DatedReminderHandled>()
        db.rawQuery(
            "SELECT note_id, day FROM dated_handled WHERE workspace = ?",
            arrayOf(workspace)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += DatedReminderHandled(cursor.getString(0), cursor.getString(1))
            }
        }
        return result
    }
}

fun NotificationSnapshotStore.datedReminderHandled(workspace: String): Set<DatedReminderHandled> =
    DatedReminderStore.read(readableDatabase, workspace)

fun NotificationSnapshotStore.markDatedReminderHandled(
    workspace: String,
    noteId: String,
    day: String
) {
    val values = ContentValues().apply {
        put("workspace", workspace)
        put("note_id", noteId)
        put("day", day)
    }
    check(
        writableDatabase.insertWithOnConflict(
            "dated_handled",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    )
}
