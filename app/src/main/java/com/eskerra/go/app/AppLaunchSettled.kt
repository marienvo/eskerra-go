package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.eskerra.go.feature.inbox.InboxUiState
import com.eskerra.go.feature.todayhub.TodayHubUiState
import kotlinx.coroutines.delay

internal const val MIN_SPLASH_HOLD_MS = 150L

/**
 * True when the gate, inbox, and Today Hub are all ready to reveal the UI.
 *
 * For [AppGateState.Ready], both [inboxUiState] and [todayHubUiState] must be non-null and
 * non-Loading so the splash is held until the first meaningful frame of the home screen.
 *
 * Bootstrap timing (spec §6.2): the podcast warm-start preload
 * ([com.eskerra.go.core.repository.PodcastCatalogSnapshotStore.read]) deliberately does **not**
 * participate in launch settlement. Episodes is a non-home tab, and phase-1 work must not block
 * first render of unrelated tabs; the snapshot is painted lazily by `PodcastsViewModel` when the
 * Episodes tab mounts (which happens at gate Ready), so warm start is coupled to gate timing
 * without holding the splash.
 */
internal fun isLaunchSettled(
    gateState: AppGateState,
    inboxUiState: InboxUiState?,
    todayHubUiState: TodayHubUiState?,
    podcastFirstLaunch: Boolean = false
): Boolean = when (gateState) {
    AppGateState.Loading -> false
    is AppGateState.NeedsSetup -> true
    is AppGateState.Ready ->
        if (podcastFirstLaunch) {
            true
        } else {
            inboxUiState != null &&
                inboxUiState !is InboxUiState.Loading &&
                todayHubUiState != null &&
                todayHubUiState !is TodayHubUiState.Loading
        }
}

/**
 * Dismisses the splash once [isLaunchSettled] becomes true.
 * Keys on the derived settled flag, not individual state objects, so
 * [InboxUiState.Content.isRefreshing] toggles do not restart the effect.
 */
@Composable
internal fun AppLaunchSettledEffect(
    gateState: AppGateState,
    inboxUiState: InboxUiState?,
    todayHubUiState: TodayHubUiState?,
    onLaunchSettled: () -> Unit,
    podcastFirstLaunch: Boolean = false,
    minSplashHoldMs: Long = MIN_SPLASH_HOLD_MS
) {
    val launchSettled = isLaunchSettled(
        gateState,
        inboxUiState,
        todayHubUiState,
        podcastFirstLaunch
    )
    LaunchedEffect(gateState, launchSettled) {
        if (!launchSettled) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (minSplashHoldMs > 0L) {
            delay(minSplashHoldMs)
        }
        onLaunchSettled()
    }
}
