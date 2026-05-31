package com.eskerra.go.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun ShellSyncButton(
    state: ShellSyncIndicatorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = state.isChecking || state.isSyncing
    val containerColor = when {
        busy -> MaterialTheme.colorScheme.surfaceContainerHigh
        state.needsAttention -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        busy -> MaterialTheme.colorScheme.onSurfaceVariant
        state.needsAttention -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    BadgedBox(
        modifier = modifier.alpha(if (state.isEnabled && !busy) 1f else 0.38f),
        badge = {
            if (state.badgeText != null && !busy) {
                Badge { Text(state.badgeText) }
            }
        }
    ) {
        SmallFloatingActionButton(
            onClick = { if (state.isEnabled && !busy) onClick() },
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync")
                }
            }
        }
    }
}
