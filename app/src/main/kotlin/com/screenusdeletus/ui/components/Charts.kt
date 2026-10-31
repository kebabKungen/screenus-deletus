package com.screenusdeletus.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenusdeletus.core.formatDuration
import com.screenusdeletus.ui.theme.ChartColors

/** Progress ring for today's usage against the goal. Turns to the error colour once over. */
@Composable
fun UsageRing(
    usedMs: Long,
    goalMs: Long,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progress: Color = MaterialTheme.colorScheme.primary,
    over: Color = MaterialTheme.colorScheme.error,
    text: Color = MaterialTheme.colorScheme.onSurface,
    subText: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    strokeWidth: Dp = 14.dp,
    showLabel: Boolean = true,
) {
    val fraction = if (goalMs > 0) usedMs.toFloat() / goalMs else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val color = if (fraction > 1f) over else progress
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = stroke)
        }
        if (showLabel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(usedMs), style = MaterialTheme.typography.headlineMedium, color = text)
                Text(
                    if (goalMs > 0) "of ${formatDuration(goalMs)}" else "today",
                    style = MaterialTheme.typography.bodySmall,
                    color = subText,
                )
            }
        }
    }
}

/** Vertical bars with an optional dashed goal line. */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    goal: Long = 0,
    height: Dp = 160.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(goal).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRoundRect(
                    color = if (goal > 0 && v > goal) over else primary,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            if (goal > 0) {
                val y = size.height - size.height * goal / max
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 7 × 24 heat map: rows are days (Mon–Sun), columns hours. */
@Composable
fun HeatMap(data: Array<LongArray>, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = data.maxOf { row -> row.maxOrNull() ?: 0L }.coerceAtLeast(1L)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(modifier) {
        data.forEachIndexed { d, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(days[d], Modifier.width(16.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val cell = size.width / 24
                    row.forEachIndexed { h, v ->
                        val a = v.toFloat() / max
                        drawRoundRect(
                            color = if (v == 0L) empty else base.copy(alpha = 0.15f + 0.85f * a),
                            topLeft = Offset(h * cell + 1, 1f),
                            size = Size(cell - 2, size.height - 2),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Donut chart with a legend. */
@Composable
fun DonutChart(slices: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1L)
    val surface = MaterialTheme.colorScheme.surface
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp)) {
            var start = -90f
            slices.forEachIndexed { i, (_, v) ->
                val sweep = 360f * v / total
                drawArc(ChartColors[i % ChartColors.size], start, sweep, false, style = Stroke(28.dp.toPx()),
                    topLeft = Offset(14.dp.toPx(), 14.dp.toPx()), size = Size(size.width - 28.dp.toPx(), size.height - 28.dp.toPx()))
                // thin separator between slices
                if (slices.size > 1) drawArc(surface, start + sweep - 0.6f, 1.2f, false, style = Stroke(28.dp.toPx()),
                    topLeft = Offset(14.dp.toPx(), 14.dp.toPx()), size = Size(size.width - 28.dp.toPx(), size.height - 28.dp.toPx()))
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            slices.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).background(ChartColors[i % ChartColors.size], CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("$label · ${formatDuration(v)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Horizontal bar used for app/trigger breakdowns. */
@Composable
fun LabeledBar(label: String, value: String, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
}
