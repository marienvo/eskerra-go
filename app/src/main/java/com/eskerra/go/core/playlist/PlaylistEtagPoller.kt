package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2ConditionalResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single-interval ETag poller, mirroring `packages/eskerra-core/src/playlistEtagPoller.ts`.
 *
 * Invariants (per spec):
 * - No overlapping requests: a timer tick is skipped while one is in flight.
 * - Immediate tick on `setActive(true)`; interval starts after the immediate tick.
 * - `setActive(false)` cancels the timer **and** aborts the in-flight tick
 *   (cooperative cancellation — CancellationException is swallowed, not forwarded to
 *   `onTransientError`).
 * - ETag is carried across ticks; reset to `null` on [R2ConditionalResult.Missing].
 * - `onRemotePlaylistCleared` fires exactly once when the state goes present → absent; it
 *   never fires on first-boot missing.
 * - `setIntervalMs` reschedules the timer without an extra immediate tick.
 *
 * @param scope Provides the [CoroutineDispatcher] for all poller coroutines. The poller owns a
 *   **standalone** [SupervisorJob] (not a child of [scope]'s job), so [dispose] is the
 *   sole stop mechanism; callers must call it when the host is destroyed.
 */
class PlaylistEtagPoller(
    initialIntervalMs: Long,
    scope: CoroutineScope,
    private val fetchConditional: suspend (etag: String?) -> R2ConditionalResult,
    private val onDataChanged: (PlaylistEntry) -> Unit,
    private val onRemotePlaylistCleared: (() -> Unit)? = null,
    private val onTransientError: ((Throwable) -> Unit)? = null
) {
    private val pollerJob = SupervisorJob()
    private val pollerScope = CoroutineScope(scope.coroutineContext + pollerJob)

    private var etag: String? = null
    private var haveRemote = false
    private var active = false
    private var intervalMs = initialIntervalMs
    private var tickJob: Job? = null
    private var timerJob: Job? = null
    private var disposed = false

    fun setActive(next: Boolean) {
        if (disposed || next == active) return
        active = next
        if (!active) {
            timerJob?.cancel()
            timerJob = null
            tickJob?.cancel()
            tickJob = null
            return
        }
        launchTick()
        scheduleTimer()
    }

    /** Reschedules the timer without an extra immediate tick. Preserves ETag state. */
    fun setIntervalMs(nextMs: Long) {
        if (disposed || nextMs == intervalMs) return
        intervalMs = nextMs
        if (!active) return
        scheduleTimer()
    }

    /** Runs one poll immediately if active and idle; skips when a request is in flight. */
    fun triggerCheck() {
        if (disposed) return
        launchTick()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        active = false
        timerJob?.cancel()
        timerJob = null
        tickJob?.cancel()
        tickJob = null
        pollerJob.cancel()
    }

    fun getEtag(): String? = etag

    private fun launchTick() {
        if (!active || disposed || tickJob?.isActive == true) return
        tickJob = pollerScope.launch {
            try {
                val result = fetchConditional(etag)
                if (!isActive) return@launch
                when (result) {
                    is R2ConditionalResult.Updated -> {
                        etag = result.etag
                        haveRemote = true
                        onDataChanged(result.entry)
                    }
                    R2ConditionalResult.Missing -> {
                        etag = null
                        if (haveRemote) {
                            haveRemote = false
                            onRemotePlaylistCleared?.invoke()
                        }
                    }
                    R2ConditionalResult.NotModified -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onTransientError?.invoke(e)
            }
        }
    }

    private fun scheduleTimer() {
        timerJob?.cancel()
        timerJob = pollerScope.launch {
            while (isActive) {
                delay(intervalMs)
                launchTick()
            }
        }
    }
}
