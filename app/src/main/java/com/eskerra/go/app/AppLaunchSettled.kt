package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.eskerra.go.feature.inbox.InboxUiState
import kotlinx.coroutines.delay

internal const val MIN_SPLASH_HOLD_MS = 150L

/** True when gate and inbox (if applicable) are ready to reveal the UI. */
internal fun isLaunchSettled(gateState: AppGateState, inboxUiState: InboxUiState?): Boolean =
    when (gateState) {
        AppGateState.Loading -> false
        is AppGateState.NeedsSetup -> true
        is AppGateState.Ready -> inboxUiState != null && inboxUiState !is InboxUiState.Loading
    }

@Composable
internal fun AppLaunchSettledEffect(
    gateState: AppGateState,
    inboxUiState: InboxUiState?,
    onLaunchSettled: () -> Unit,
    minSplashHoldMs: Long = MIN_SPLASH_HOLD_MS
) {
    LaunchedEffect(gateState, inboxUiState) {
        if (!isLaunchSettled(gateState, inboxUiState)) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (minSplashHoldMs > 0L) {
            delay(minSplashHoldMs)
        }
        onLaunchSettled()
    }
}
