package io.github.sjakubiak.fitboi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sjakubiak.fitboi.core.Goals
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import io.github.sjakubiak.fitboi.core.Readiness
import io.github.sjakubiak.fitboi.ui.Format
import io.github.sjakubiak.fitboi.ui.components.DoubleRing
import io.github.sjakubiak.fitboi.ui.components.MetricTile
import io.github.sjakubiak.fitboi.ui.components.ProgressRing
import io.github.sjakubiak.fitboi.ui.components.SectionCard
import io.github.sjakubiak.fitboi.ui.theme.HealthColors
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(snapshot: HealthSnapshot, goals: Goals, onOpen: (Tab) -> Unit) {
    val today = snapshot.todayActivity
    val night = snapshot.lastNight?.takeIf { !it.date.isBefore(snapshot.today.minusDays(1)) }

    ScreenColumn {
        Text(Format.longDate(snapshot.today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Today", style = MaterialTheme.typography.displaySmall)

        snapshot.readiness?.let { ReadinessCard(it) }

        SectionCard(onClick = { onOpen(Tab.Activity) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoubleRing(
                    outer = (today?.steps ?: 0).toFloat() / goals.steps,
                    inner = (today?.activeZoneMinutes ?: 0).toFloat() / goals.activeZoneMinutes,
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingStat("Steps", Format.count(today?.steps ?: 0), "of ${Format.count(goals.steps)}", HealthColors.Steps)
                    RingStat("Active zone min", "${today?.activeZoneMinutes ?: 0}", "of ${goals.activeZoneMinutes}", HealthColors.ActiveZone)
                }
            }
        }

        TileRow {
            MetricTile(
                Icons.Filled.Place, HealthColors.Distance, "Distance", Format.km(today?.distanceKm ?: 0.0), "km",
                Modifier.weight(1f), onClick = { onOpen(Tab.Activity) },
            )
            MetricTile(
                Icons.Filled.LocalFireDepartment, HealthColors.Calories, "Calories",
                today?.caloriesKcal?.let { Format.count(it) } ?: "–", "kcal",
                Modifier.weight(1f), onClick = { onOpen(Tab.Activity) },
            )
        }
        TileRow {
            MetricTile(
                Icons.Filled.Favorite, HealthColors.Heart, "Resting HR",
                snapshot.restingHeartRate.lastOrNull()?.value?.let { Format.count(it) } ?: "–", "bpm",
                Modifier.weight(1f), onClick = { onOpen(Tab.Heart) },
            )
            MetricTile(
                Icons.Filled.MonitorHeart, HealthColors.Hrv, "HRV",
                snapshot.hrv.lastOrNull()?.value?.let { Format.count(it) } ?: "–", "ms",
                Modifier.weight(1f), onClick = { onOpen(Tab.Heart) },
            )
        }
        TileRow {
            MetricTile(
                Icons.Filled.Bedtime, HealthColors.Sleep, "Sleep",
                night?.let { Format.minutes(it.minutesAsleep) } ?: "–",
                modifier = Modifier.weight(1f),
                caption = night?.let { "${Format.clock(it.start)} – ${Format.clock(it.end)}" },
                onClick = { onOpen(Tab.Sleep) },
            )
            MetricTile(
                Icons.AutoMirrored.Filled.DirectionsWalk, HealthColors.Steps, "Workouts",
                "${workoutsThisWeek(snapshot)}",
                modifier = Modifier.weight(1f),
                caption = "last 7 days",
                onClick = { onOpen(Tab.Activity) },
            )
        }
    }
}

private fun workoutsThisWeek(snapshot: HealthSnapshot): Int {
    val weekStart = snapshot.today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant()
    return snapshot.workouts.count { !it.start.isBefore(weekStart) }
}

@Composable
private fun ReadinessCard(readiness: Readiness) {
    SectionCard(title = "Readiness", subtitle = "Fitboi's estimate from your HRV, resting heart rate and sleep") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(readiness.score / 100f, HealthColors.Readiness, size = 96.dp, stroke = 10.dp) {
                Text("${readiness.score}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(readiness.label, style = MaterialTheme.typography.titleLarge)
                readiness.hrvDeltaPct?.let {
                    Detail("HRV ${if (it >= 0) "up" else "down"} ${abs(it).roundToInt()}% vs your usual")
                }
                readiness.restingHrDelta?.let {
                    val bpm = abs(it).roundToInt()
                    Detail(if (bpm == 0) "Resting HR at your usual" else "Resting HR ${if (it > 0) "up" else "down"} $bpm bpm")
                }
                readiness.sleepHours?.let { Detail("Slept ${Format.minutes((it * 60).toLong())}") }
            }
        }
    }
}

@Composable
private fun Detail(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun RingStat(label: String, value: String, goal: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Text(goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
internal fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
