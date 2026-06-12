package com.eskerra.go.feature.todayhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.todayhub.TodayHubWeeks.ProgressSegment
import com.eskerra.go.core.todayhub.TodayHubWeeks.SegmentKind

/**
 * Week progress strip for the date column header (spec §11.5): one bar per day, or six bars with a
 * wide merged weekend, coloured by [SegmentKind]. Segment widths come from `weekProgressSegments`
 * (`widthPx` is used as a layout weight, so a merged weekend is twice as wide).
 */
@Composable
fun TodayHubWeekProgressStrip(segments: List<ProgressSegment>, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        segments.forEach { segment -> ProgressBar(segment) }
    }
}

@Composable
private fun RowScope.ProgressBar(segment: ProgressSegment) {
    val color = when (segment.kind) {
        SegmentKind.FILLED -> MaterialTheme.colorScheme.primary
        SegmentKind.CURRENT -> MaterialTheme.colorScheme.tertiary
        SegmentKind.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .weight(segment.widthPx.toFloat().coerceAtLeast(1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}
