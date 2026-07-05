package com.eskerra.go.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eskerra.go.ui.theme.EskerraChromeTokens

/** Height shared by every floating chrome control so pills and round buttons align. */
internal val ShellChromeButtonSize = 40.dp

/**
 * Shared visual primitive for the floating chrome controls (Notes, Podcasts, Sync, Menu).
 *
 * A flat [Surface] with a fully rounded shape (50%), matte container color and a subtle border.
 * Icon-only content renders as a circle; wider label content renders as a pill. All four controls
 * therefore share one color and border treatment.
 */
@Composable
internal fun ShellChromeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minWidth = ShellChromeButtonSize,
            minHeight = ShellChromeButtonSize
        ),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, EskerraChromeTokens.ChromeButtonBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalContentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
