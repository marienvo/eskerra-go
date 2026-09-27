package com.eskerra.go.data.perf

import android.util.Log
import com.eskerra.go.BuildConfig

/**
 * Debug-only breadcrumb log for a note's navigation journey: prefetch submit, per-target warm,
 * tap, reader load/publish, first frame. Unlike [ColdStartTrace] this is not a process-wide
 * collector — several notes' journeys can overlap (a background prefetch batch, a tap landing on
 * one of its targets), so there is no accumulated sample, only breadcrumbs correlated by the
 * note's id (in the `detail` string) and by logcat's own per-line timestamp.
 *
 * Read with `adb logcat -s NoteNav`: a note's full journey is every line mentioning its id, in
 * timestamp order. `prefetch.submit` → `warm.done` (did it finish before the tap?) → `tap.start` /
 * `tap.warmed` / `tap.navigate` → `reader.load.start` / `reader.content` / `reader.prepared` /
 * `reader.published` → `reader.firstFrame` (the gap between `reader.published` and
 * `reader.firstFrame` is composition + layout cost, which no cache can shrink).
 *
 * Release builds emit nothing ([BuildConfig.DEBUG] guard).
 */
object NoteNavTrace {
    const val TAG = "NoteNav"

    fun log(event: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.i(TAG, if (detail.isEmpty()) event else "$event $detail")
        }
    }
}
