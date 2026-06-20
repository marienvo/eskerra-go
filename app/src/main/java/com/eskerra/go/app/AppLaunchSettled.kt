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
 */
internal fun isLaunchSettled(
    gateState: AppGateState,
    inboxUiState: InboxUiState?,
    todayHubUiState: TodayHubUiState?
): Boolean = when (gateState) {
    AppGateState.Loading -> false
    is AppGateState.NeedsSetup -> true
    is AppGateState.Ready ->
        inboxUiState != null &&
            inboxUiState !is InboxUiState.Loading &&
            todayHubUiState != null &&
            todayHubUiState !is TodayHubUiState.Loading
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
    minSplashHoldMs: Long = MIN_SPLASH_HOLD_MS
) {
    val launchSettled = isLaunchSettled(gateState, inboxUiState, todayHubUiState)
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
