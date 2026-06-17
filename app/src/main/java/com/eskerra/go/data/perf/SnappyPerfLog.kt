package com.eskerra.go.data.perf

import android.util.Log

/**
 * Throwaway timing logs for the snappy boot & navigation baseline (see
 * [specs/plans/snappy-boot-and-navigation-plan.md]).
 *
 * Filter logcat with tag `EskerraPerf`. Remove once before/after numbers are captured.
 */
internal object SnappyPerfLog {
    const val TAG = "EskerraPerf"

    fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    fun log(event: String, durationMs: Long, extras: Map<String, Any> = emptyMap()) {
        val detail = extras.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        val message = if (detail.isEmpty()) {
            "$event durationMs=$durationMs"
        } else {
            "$event durationMs=$durationMs $detail"
        }
        runCatching { Log.i(TAG, message) }
    }
}
