package com.eskerra.go.data.debug

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * TEMPORARY cold-start instrumentation for `specs/performance/cold-start-debug-logbook.md`.
 *
 * Every mark is stamped with milliseconds since process start, so a logcat capture reconstructs
 * the launch timeline against the same zero point the logbook uses for launch-settled. Revert
 * this file and its call sites once the measurement round is recorded.
 *
 * Read with:
 * `adb logcat -s BootTrace`
 *
 * Unit tests run on a plain JVM without Robolectric, where the android.* stubs throw instead of
 * returning values, so every call is guarded and degrades to a no-op off-device.
 */
object BootTrace {

    const val TAG = "BootTrace"

    /** Milliseconds since this process started, or -1 when the framework is unavailable. */
    fun sinceStartMs(): Long = runCatching {
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }.getOrDefault(-1L)

    fun mark(event: String, detail: String = "") {
        runCatching {
            Log.i(
                TAG,
                buildString {
                    append(sinceStartMs())
                    append("ms ")
                    append(event)
                    if (detail.isNotEmpty()) {
                        append(' ')
                        append(detail)
                    }
                }
            )
        }
    }

    /** Marks [event] with the elapsed time of a block that started at [startedAtMs]. */
    fun markSince(event: String, startedAtMs: Long, detail: String = "") {
        val took = sinceStartMs() - startedAtMs
        mark(event, "took=${took}ms" + if (detail.isEmpty()) "" else " $detail")
    }
}
