package com.eskerra.go.feature.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val ROTATION_PERIOD_MS = 1100
private const val ARC_SWEEP_DEGREES = 120f
private const val ARROWHEAD_HALF_ANGLE_DEGREES = 24f

/**
 * Two-arrow sync glyph, rotating in place. Renders in a square [Canvas] where every coordinate is
 * derived from the draw scope's own [androidx.compose.ui.graphics.drawscope.DrawScope.center] and one
 * `radius`, and the rotation pivots on that same center — so the figure cannot wobble off its own
 * axis the way rotating a pre-baked vector icon can (a vector's drawn centroid rarely coincides with
 * its viewport center). Meant to sit inside the same [androidx.compose.material3.Badge] slot the
 * pending-change count uses, so it inherits that badge's footprint and content color.
 */
@Composable
fun SyncSpinner(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 1.5.dp
) {
    val transition = rememberInfiniteTransition(label = "sync-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ROTATION_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync-spinner-angle"
    )
    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        rotate(degrees = angle, pivot = center) {
            drawArrow(startAngleDegrees = 0f, radius = radius, color = color, stroke = stroke)
            drawArrow(startAngleDegrees = 180f, radius = radius, color = color, stroke = stroke)
        }
    }
}

/** One 120° arc plus an arrowhead tangent to the same circle at its leading edge. */
private fun DrawScope.drawArrow(
    startAngleDegrees: Float,
    radius: Float,
    color: Color,
    stroke: Stroke
) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    drawArc(
        color = color,
        startAngle = startAngleDegrees,
        sweepAngle = ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke
    )

    val tipAngleDegrees = startAngleDegrees + ARC_SWEEP_DEGREES
    val tip = pointOnCircle(radius, tipAngleDegrees)
    val leftWing = pointOnCircle(radius, tipAngleDegrees - ARROWHEAD_HALF_ANGLE_DEGREES)
    val rightWing = pointOnCircle(radius, tipAngleDegrees + ARROWHEAD_HALF_ANGLE_DEGREES)
    val path = Path().apply {
        moveTo(leftWing.x, leftWing.y)
        lineTo(tip.x, tip.y)
        lineTo(rightWing.x, rightWing.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke.width, cap = StrokeCap.Round)
    )
}

private fun DrawScope.pointOnCircle(radius: Float, angleDegrees: Float): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = center.x + radius * cos(radians).toFloat(),
        y = center.y + radius * sin(radians).toFloat()
    )
}
