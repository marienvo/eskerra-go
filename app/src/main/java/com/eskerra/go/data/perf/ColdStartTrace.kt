package com.eskerra.go.data.perf

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.eskerra.go.BuildConfig
import com.eskerra.go.core.model.ColdStartSample

/**
 * Process-wide collector for the current cold start.
 *
 * Marks arrive from the main thread (activity, gate) and from IO threads (registry, scanner), so
 * every field is volatile and every phase keeps only its first occurrence — a cold start happens
 * once per process, and later refreshes must not overwrite it.
 *
 * In debug builds each mark is also written to logcat under the `BootTrace` tag, which is what
 * `scripts/measure-cold-start.sh` parses. Release builds collect silently.
 *
 * Every framework call is guarded: unit tests run on a plain JVM without Robolectric, where the
 * android.* stubs throw instead of returning, so this degrades to a no-op off-device.
 */
object ColdStartTrace {

    const val TAG = "BootTrace"

    private const val UNSET = -1L

    @Volatile private var activityOnCreateMs = UNSET

    @Volatile private var diBuiltMs = UNSET

    @Volatile private var gateStartMs = UNSET

    @Volatile private var gateReadyMs = UNSET

    @Volatile private var fingerprintHit = false

    @Volatile private var snapshotReadSeen = false

    @Volatile private var snapshotReadMs = 0L

    @Volatile private var snapshotHit = false

    @Volatile private var firstScanMs = UNSET

    @Volatile private var firstScanEndMs = UNSET

    @Volatile private var noteCount = 0

    @Volatile private var registryRefreshSeen = false

    @Volatile private var hadMemoBase = false

    @Volatile private var settled = false

    @Volatile private var pendingSample: ColdStartSample? = null

    /** Milliseconds since this process started, or -1 when the framework is unavailable. */
    fun sinceStartMs(): Long = runCatching {
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }.getOrDefault(UNSET)

    fun markActivityOnCreate() {
        if (activityOnCreateMs == UNSET) activityOnCreateMs = sinceStartMs()
        log("activity.onCreate")
    }

    fun markDiBuilt() {
        if (diBuiltMs == UNSET) diBuiltMs = sinceStartMs()
        log("di.built")
    }

    fun markGateStart() {
        if (gateStartMs == UNSET) gateStartMs = sinceStartMs()
        log("gate.start")
    }

    fun markGateReady(fingerprintHit: Boolean, path: String) {
        if (gateReadyMs == UNSET) {
            gateReadyMs = sinceStartMs()
            this.fingerprintHit = fingerprintHit
        }
        log("gate.ready", "path=$path fingerprintHit=$fingerprintHit")
    }

    fun markSnapshotRead(tookMs: Long, hit: Boolean, notes: Int) {
        if (!settled && !snapshotReadSeen) {
            snapshotReadSeen = true
            snapshotReadMs = tookMs
            snapshotHit = hit
            if (hit) noteCount = notes
        }
        log("registry.current", "took=${tookMs}ms snapshotHit=$hit notes=$notes")
    }

    fun markScan(tookMs: Long, visited: Int, reRead: Int) {
        if (!settled && firstScanMs == UNSET) {
            firstScanMs = tookMs
            firstScanEndMs = sinceStartMs()
            noteCount = visited
        }
        log("scanner.scan", "took=${tookMs}ms visited=$visited reRead=$reRead")
    }

    fun markRegistryRefresh(tookMs: Long, hadMemoBase: Boolean) {
        if (!settled && !registryRefreshSeen) {
            registryRefreshSeen = true
            this.hadMemoBase = hadMemoBase
        }
        log("registry.refresh", "took=${tookMs}ms hadMemoBase=$hadMemoBase")
    }

    /**
     * Closes the launch and builds the sample. Returns nothing itself; the sample is picked up by
     * [consumeSample] once the app is past its first frame, so persisting it never touches the
     * launch path.
     */
    fun markSettled() {
        if (settled) return
        settled = true
        val totalMs = sinceStartMs()
        log("launch-settled")
        if (activityOnCreateMs == UNSET || gateStartMs == UNSET || gateReadyMs == UNSET) {
            // An incomplete launch (setup screen, or the framework unavailable) is not comparable
            // to a steady-state cold start, so it is dropped rather than reported as a fast one.
            return
        }
        val scanEnd = if (firstScanEndMs == UNSET) gateReadyMs else firstScanEndMs
        pendingSample = ColdStartSample(
            processToActivityMs = activityOnCreateMs,
            diBuildMs = (diBuiltMs - activityOnCreateMs).coerceAtLeast(0),
            toGateStartMs = (gateStartMs - diBuiltMs).coerceAtLeast(0),
            gateResolveMs = (gateReadyMs - gateStartMs).coerceAtLeast(0),
            snapshotReadMs = snapshotReadMs,
            firstScanMs = if (firstScanMs == UNSET) 0L else firstScanMs,
            settleTailMs = (totalMs - scanEnd).coerceAtLeast(0),
            totalMs = totalMs,
            noteCount = noteCount,
            fingerprintHit = fingerprintHit,
            snapshotHit = snapshotHit,
            hadMemoBase = hadMemoBase
        )
    }

    /** Returns the finished sample once, so a recomposition cannot record it twice. */
    fun consumeSample(): ColdStartSample? {
        val sample = pendingSample
        pendingSample = null
        return sample
    }

    /** Debug-only breadcrumb; the collected sample is what release builds actually report. */
    fun log(event: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
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
}
