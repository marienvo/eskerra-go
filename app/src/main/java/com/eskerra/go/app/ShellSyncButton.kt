package com.eskerra.go.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShellSyncButton(
    state: ShellSyncIndicatorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = state.isChecking || state.isSyncing
    val enabled = state.isEnabled && !busy

    BadgedBox(
        modifier = modifier,
        badge = {
            if (state.badgeText != null && !busy) {
                Badge { Text(state.badgeText) }
            }
        }
    ) {
        ShellChromeButton(onClick = { if (enabled) onClick() }) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync")
                }
            }
        }
    }
}
