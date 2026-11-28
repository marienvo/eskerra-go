package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

@Composable
internal fun NotificationOpenEffect(
    request: NotificationOpenRequest,
    launchSettled: Boolean,
    navigate: (String) -> Unit
) {
    var handledRequestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(request, launchSettled) {
        // Home must report the initial inbox state before a notification can leave it.
        val destination = request.destination(launchSettled, handledRequestId)
            ?: return@LaunchedEffect
        withFrameNanos { }
        navigate(destination)
        handledRequestId = request.requestId
    }
}
