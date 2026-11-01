package com.eskerra.go.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun NotificationSettingsSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SectionTitle("Notifications")
    Text(
        "Sync in the background and notify while desktop is inactive.",
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Enable notifications")
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}
