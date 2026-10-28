package io.github.sjakubiak.fitboi.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sjakubiak.fitboi.core.Goals
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import io.github.sjakubiak.fitboi.core.SleepNight
import io.github.sjakubiak.fitboi.core.SleepStage
import io.github.sjakubiak.fitboi.ui.Format
import io.github.sjakubiak.fitboi.ui.components.BarChart
import io.github.sjakubiak.fitboi.ui.components.EmptyNote
import io.github.sjakubiak.fitboi.ui.components.Hypnogram
import io.github.sjakubiak.fitboi.ui.components.LegendDot
import io.github.sjakubiak.fitboi.ui.components.SectionCard
import io.github.sjakubiak.fitboi.ui.components.stageColor
import io.github.sjakubiak.fitboi.ui.theme.HealthColors

@Composable
fun SleepScreen(snapshot: HealthSnapshot, goals: Goals) {
    val night = snapshot.lastNight
    val week = (6 downTo 0).map { snapshot.today.minusDays(it.toLong()) }
        .map { date -> date to snapshot.sleep.firstOrNull { it.date == date } }

    ScreenColumn {
        Text("Sleep", style = MaterialTheme.typography.displaySmall)

        if (night == null) {
            SectionCard { EmptyNote("No sleep recorded in the last two weeks. Wear your Fitbit Air to bed and sync the Google Health app.") }
            return@ScreenColumn
        }

        SectionCard(
            title = if (night.date == snapshot.today) "Last night" else Format.longDate(night.date),
            subtitle = "${Format.clock(night.start)} – ${Format.clock(night.end)}",
        ) {
            Text(Format.minutes(night.minutesAsleep), style = MaterialTheme.typography.displayMedium)
            Text(
                "asleep · ${Format.minutes(night.minutesAwake)} awake",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (night.segments.isNotEmpty()) {
                Hypnogram(night.segments, night.start, night.end, Modifier.padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(Format.clock(night.start), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(Format.clock(night.end), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (night.stageMinutes.isNotEmpty()) {
            SectionCard(title = "Sleep stages") { StageBreakdown(night) }
        }

        SectionCard(title = "Time asleep", subtitle = "Last 7 nights · goal ${Format.minutes(goals.sleepMinutes)}") {
            val asleep = week.mapNotNull { it.second?.minutesAsleep }
            if (asleep.isNotEmpty()) {
                Text("Average ${Format.minutes(asleep.average().toLong())}", style = MaterialTheme.typography.headlineSmall)
            }
            BarChart(
                values = week.map { (it.second?.minutesAsleep ?: 0) / 60f },
                labels = week.map { Format.weekday(it.first) },
                color = HealthColors.Sleep,
                goal = goals.sleepMinutes / 60f,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun StageBreakdown(night: SleepNight) {
    val order = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.ASLEEP, SleepStage.RESTLESS)
    val total = night.stageMinutes.values.sum().coerceAtLeast(1)
    order.forEach { stage ->
        val minutes = night.stageMinutes[stage] ?: return@forEach
        LegendDot(
            stageColor(stage),
            stage.name.lowercase().replaceFirstChar(Char::uppercase),
            "${Format.minutes(minutes)} · ${minutes * 100 / total}%",
        )
    }
}
