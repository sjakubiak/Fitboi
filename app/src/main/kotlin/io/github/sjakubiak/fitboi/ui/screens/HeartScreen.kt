package io.github.sjakubiak.fitboi.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sjakubiak.fitboi.core.DailyValue
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import io.github.sjakubiak.fitboi.ui.Format
import io.github.sjakubiak.fitboi.ui.components.EmptyNote
import io.github.sjakubiak.fitboi.ui.components.LineChart
import io.github.sjakubiak.fitboi.ui.components.SectionCard
import io.github.sjakubiak.fitboi.ui.theme.HealthColors

@Composable
fun HeartScreen(snapshot: HealthSnapshot) {
    val samples = snapshot.heartToday

    ScreenColumn {
        Text("Heart", style = MaterialTheme.typography.displaySmall)

        SectionCard(title = "Heart rate today", subtitle = "10-minute averages") {
            if (samples.isEmpty()) {
                EmptyNote("No heart rate data yet today.")
            } else {
                val low = samples.mapNotNull { it.minBpm }.minOrNull() ?: samples.minOf { it.avgBpm }
                val high = samples.mapNotNull { it.maxBpm }.maxOrNull() ?: samples.maxOf { it.avgBpm }
                Text("${Format.count(low)}–${Format.count(high)} bpm", style = MaterialTheme.typography.headlineMedium)
                LineChart(
                    values = samples.map { it.avgBpm.toFloat() },
                    color = HealthColors.Heart,
                    showDots = false,
                    band = samples.map { ((it.minBpm ?: it.avgBpm).toFloat()) to ((it.maxBpm ?: it.avgBpm).toFloat()) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(Format.clock(samples.first().time), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(Format.clock(samples.last().time), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        TrendCard("Resting heart rate", "bpm", snapshot.restingHeartRate, HealthColors.Heart)
        TrendCard("Heart rate variability", "ms", snapshot.hrv, HealthColors.Hrv)
    }
}

@Composable
private fun TrendCard(title: String, unit: String, values: List<DailyValue>, color: Color) {
    SectionCard(title = title, subtitle = "Last 30 days") {
        val latest = values.lastOrNull()
        if (latest == null) {
            EmptyNote("No data yet. This usually appears after a night of wearing your Fitbit Air.")
            return@SectionCard
        }
        Text("${Format.count(latest.value)} $unit", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${Format.shortDate(latest.date)} · 30-day average ${Format.count(values.map { it.value }.average())} $unit",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LineChart(values.map { it.value.toFloat() }, color, Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(Format.shortDate(values.first().date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(Format.shortDate(latest.date), style = MaterialTheme.typography.labelSmall)
        }
    }
}
