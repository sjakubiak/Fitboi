package io.github.sjakubiak.fitboi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sjakubiak.fitboi.core.SleepSegment
import io.github.sjakubiak.fitboi.core.SleepStage
import io.github.sjakubiak.fitboi.ui.theme.HealthColors
import java.time.Instant

/** A progress ring like the Google Health app's daily goals. [progress] can exceed 1. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    content: @Composable () -> Unit = {},
) {
    val track = color.copy(alpha = 0.15f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(this.size.width - width, this.size.height - width)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Two concentric rings, outer for steps and inner for active zone minutes. */
@Composable
fun DoubleRing(outer: Float, inner: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ProgressRing(outer, HealthColors.Steps, size = 168.dp, stroke = 14.dp)
        ProgressRing(inner, HealthColors.ActiveZone, size = 128.dp, stroke = 14.dp)
        content()
    }
}

/** Vertical bars with optional dashed goal line and one label per bar. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    highlightLast: Boolean = true,
    height: Dp = 140.dp,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val max = (values + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1f
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = slot * 0.55f
            values.forEachIndexed { i, v ->
                val h = size.height * (v / max)
                val faded = highlightLast && i != values.lastIndex
                drawRoundRect(
                    color = if (faded) color.copy(alpha = 0.55f) else color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
            goal?.let {
                val y = size.height * (1 - it / max)
                drawLine(
                    gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach {
                Text(
                    it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = labelColor, textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/** A smooth-enough line with dots, scaled to its own min and max. */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    showDots: Boolean = true,
    band: List<Pair<Float, Float>>? = null,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val all = values + band.orEmpty().flatMap { listOf(it.first, it.second) }
        val lo = all.min()
        val hi = all.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1f
        val pad = 8.dp.toPx()
        fun x(i: Int) = if (values.size == 1) size.width / 2 else pad + i * (size.width - 2 * pad) / (values.size - 1)
        fun y(v: Float) = pad + (size.height - 2 * pad) * (1 - (v - lo) / span)

        band?.forEachIndexed { i, (min, max) ->
            drawLine(color.copy(alpha = 0.2f), Offset(x(i), y(min)), Offset(x(i), y(max)), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        val path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showDots) values.forEachIndexed { i, v -> drawCircle(color, 3.5.dp.toPx(), Offset(x(i), y(v))) }
    }
}

private val hypnogramRows = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE, SleepStage.RESTLESS -> HealthColors.Awake
    SleepStage.REM -> HealthColors.Rem
    SleepStage.LIGHT, SleepStage.ASLEEP -> HealthColors.Light
    SleepStage.DEEP -> HealthColors.Deep
}

private fun rowOf(stage: SleepStage) = when (stage) {
    SleepStage.RESTLESS -> 0
    SleepStage.ASLEEP -> 2
    else -> hypnogramRows.indexOf(stage)
}

/** Sleep stages over the night, one row per stage, like the Google Health sleep chart. */
@Composable
fun Hypnogram(segments: List<SleepSegment>, start: Instant, end: Instant, modifier: Modifier = Modifier) {
    val total = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1).toFloat()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceAround) {
            hypnogramRows.forEach {
                Text(
                    it.name.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(Modifier.weight(1f).height(140.dp)) {
            val rowHeight = size.height / hypnogramRows.size
            val barHeight = rowHeight * 0.6f
            segments.forEach { seg ->
                val x0 = size.width * ((seg.start.toEpochMilli() - start.toEpochMilli()) / total)
                val x1 = size.width * ((seg.end.toEpochMilli() - start.toEpochMilli()) / total)
                val row = rowOf(seg.stage)
                drawRoundRect(
                    color = stageColor(seg.stage),
                    topLeft = Offset(x0, row * rowHeight + (rowHeight - barHeight) / 2),
                    size = Size((x1 - x0).coerceAtLeast(1.5f), barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
    }
}
