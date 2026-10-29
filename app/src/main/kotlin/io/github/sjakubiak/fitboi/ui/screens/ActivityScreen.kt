package io.github.sjakubiak.fitboi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sjakubiak.fitboi.core.DayActivity
import io.github.sjakubiak.fitboi.core.Goals
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import io.github.sjakubiak.fitboi.core.Workout
import io.github.sjakubiak.fitboi.ui.Format
import io.github.sjakubiak.fitboi.ui.components.BarChart
import io.github.sjakubiak.fitboi.ui.components.EmptyNote
import io.github.sjakubiak.fitboi.ui.components.MetricTile
import io.github.sjakubiak.fitboi.ui.components.SectionCard
import io.github.sjakubiak.fitboi.ui.theme.HealthColors
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ActivityScreen(snapshot: HealthSnapshot, goals: Goals) {
    val week = lastDays(snapshot.activity, snapshot.today, 7)
    val month = lastDays(snapshot.activity, snapshot.today, 30)
    val today = snapshot.todayActivity

    ScreenColumn {
        Text("Activity", style = MaterialTheme.typography.displaySmall)

        SectionCard(title = "Steps", subtitle = "Last 7 days · goal ${Format.count(goals.steps)}") {
            Text(Format.count(today?.steps ?: 0), style = MaterialTheme.typography.displayMedium)
            Text(
                "30-day average ${Format.count(month.map { it.steps }.average().takeIf { !it.isNaN() } ?: 0.0)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarChart(
                values = week.map { it.steps.toFloat() },
                labels = week.map { Format.weekday(it.date) },
                color = HealthColors.Steps,
                goal = goals.steps.toFloat(),
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        SectionCard(title = "Active zone minutes", subtitle = "Last 7 days · daily goal ${goals.activeZoneMinutes}") {
            Text("${week.sumOf { it.activeZoneMinutes }} this week", style = MaterialTheme.typography.headlineSmall)
            BarChart(
                values = week.map { it.activeZoneMinutes.toFloat() },
                labels = week.map { Format.weekday(it.date) },
                color = HealthColors.ActiveZone,
                goal = goals.activeZoneMinutes.toFloat(),
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        TileRow {
            MetricTile(
                Icons.Filled.Place, HealthColors.Distance, "Distance today", Format.km(today?.distanceKm ?: 0.0), "km",
                Modifier.weight(1f), caption = "${Format.km(week.sumOf { it.distanceKm })} km this week",
            )
            MetricTile(
                Icons.Filled.LocalFireDepartment, HealthColors.Calories, "Calories today",
                today?.caloriesKcal?.let { Format.count(it) } ?: "–", "kcal", Modifier.weight(1f),
            )
        }

        SectionCard(title = "Workouts", subtitle = "Last 30 days") {
            if (snapshot.workouts.isEmpty()) EmptyNote("No workouts recorded yet.")
            snapshot.workouts.take(15).forEachIndexed { i, workout ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                WorkoutRow(workout)
            }
        }
    }
}

@Composable
private fun WorkoutRow(workout: Workout) {
    val date = workout.start.atZone(ZoneId.systemDefault()).toLocalDate()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${Format.shortDate(date)} · ${Format.clock(workout.start)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Format.minutes(workout.duration.toMinutes()), fontWeight = FontWeight.Medium)
            val details = listOfNotNull(
                workout.distanceKm?.let { "${Format.km(it)} km" },
                workout.caloriesKcal?.let { "${Format.count(it)} kcal" },
                workout.averageHeartRate?.let { "$it bpm" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The last [count] days ending today, filling days with no data with zeros so charts stay aligned. */
internal fun lastDays(activity: List<DayActivity>, today: LocalDate, count: Int): List<DayActivity> {
    val byDate = activity.associateBy { it.date }
    return (count - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        byDate[date] ?: DayActivity(date, 0, 0.0, null, 0)
    }
}
