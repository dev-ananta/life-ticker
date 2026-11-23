package com.lifeticker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lifeticker.data.MAX_VALUE
import com.lifeticker.data.MIN_VALUE
import com.lifeticker.data.Series
import kotlin.math.abs

private const val Y_PAD = 0.6f

private fun DrawScope.xOf(t: Long, s: Series): Float {
    val span = (s.xEnd - s.xStart).toFloat()
    return if (span <= 0f) size.width / 2f else (t - s.xStart) / span * size.width
}

private fun DrawScope.yOf(v: Float, topPad: Float, bottomPad: Float): Float {
    val lo = MIN_VALUE - Y_PAD
    val hi = MAX_VALUE + Y_PAD
    val h = size.height - topPad - bottomPad
    return topPad + (1f - (v - lo) / (hi - lo)) * h
}

private fun DrawScope.linePath(s: Series, topPad: Float, bottomPad: Float): Path = Path().apply {
    s.points.forEachIndexed { i, p ->
        val x = xOf(p.t, s)
        val y = yOf(p.v, topPad, bottomPad)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/** Big interactive ticker chart. Drag a finger across it to scrub through history. */
@Composable
fun TickerChart(
    series: Series?,
    color: Color,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(series) {
            if (series == null) return@pointerInput
            fun nearest(x: Float): Int {
                val span = (series.xEnd - series.xStart).toFloat().coerceAtLeast(1f)
                var best = 0
                var bestDist = Float.MAX_VALUE
                series.points.forEachIndexed { i, p ->
                    val px = (p.t - series.xStart) / span * size.width
                    val d = abs(px - x)
                    if (d < bestDist) { bestDist = d; best = i }
                }
                return best
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                onScrub(nearest(down.position.x))
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    onScrub(nearest(change.position.x))
                    change.consume()
                }
                onScrub(null)
            }
        }
    ) {
        val topPad = 12.dp.toPx()
        val bottomPad = 12.dp.toPx()

        // Zero line ("break-even")
        val zeroY = yOf(0f, topPad, bottomPad)
        drawLine(
            color = Palette.Divider,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )

        if (series == null) return@Canvas

        val line = linePath(series, topPad, bottomPad)

        // Soft gradient under the line
        val firstX = xOf(series.points.first().t, series)
        val lastX = xOf(series.points.last().t, series)
        val fill = Path().apply {
            addPath(line)
            lineTo(lastX, size.height)
            lineTo(firstX, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)),
        )
        drawPath(
            line, color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val idx = scrubIndex?.takeIf { it in series.points.indices }
        if (idx != null) {
            val p = series.points[idx]
            val x = xOf(p.t, series)
            val y = yOf(p.v, topPad, bottomPad)
            drawLine(Palette.TextDim, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, y))
        } else {
            // Pulse dot on the latest value, like a live ticker
            val p = series.points.last()
            val c = Offset(xOf(p.t, series), yOf(p.v, topPad, bottomPad))
            drawCircle(color.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = c)
            drawCircle(color, radius = 4.dp.toPx(), center = c)
        }
    }
}

/** Small non-interactive chart for list rows. */
@Composable
fun Sparkline(series: Series?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (series == null) {
            val y = size.height / 2f
            drawLine(Palette.Divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        val pad = 2.dp.toPx()
        // Spark lines auto-scale so small moves are still visible.
        val lo = series.low
        val hi = series.high
        val span = (hi - lo).takeIf { it > 0f } ?: 1f
        val path = Path()
        series.points.forEachIndexed { i, p ->
            val x = xOf(p.t, series)
            val y = if (hi == lo) size.height / 2f else pad + (1f - (p.v - lo) / span) * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
