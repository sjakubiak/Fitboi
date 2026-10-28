package io.github.sjakubiak.fitboi.core

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A simple, transparent readiness estimate: compares last night's HRV and resting heart rate with the
 * person's own 14-day baseline, then adjusts for sleep duration. It is not Google's Readiness score.
 */
object ReadinessCalculator {
    private const val BASELINE_DAYS = 14L

    fun compute(
        today: LocalDate,
        hrv: List<DailyValue>,
        restingHr: List<DailyValue>,
        lastNight: SleepNight?,
    ): Readiness? {
        val hrvDeltaPct = deltaFromBaseline(today, hrv)?.let { (latest, baseline) -> (latest - baseline) / baseline * 100 }
        val rhrDelta = deltaFromBaseline(today, restingHr)?.let { (latest, baseline) -> latest - baseline }
        val sleepHours = lastNight?.takeIf { !it.date.isBefore(today.minusDays(1)) }?.minutesAsleep?.div(60.0)
        if (hrvDeltaPct == null && rhrDelta == null && sleepHours == null) return null

        var score = 60.0
        hrvDeltaPct?.let { score += (it * 1.5).coerceIn(-25.0, 25.0) }
        rhrDelta?.let { score += (-it * 4).coerceIn(-15.0, 15.0) }
        sleepHours?.let { score += ((it - 7) * 8).coerceIn(-20.0, 10.0) }
        val rounded = score.roundToInt().coerceIn(0, 100)

        return Readiness(
            score = rounded,
            label = when {
                rounded >= 80 -> "Ready to push"
                rounded >= 60 -> "Good to go"
                rounded >= 40 -> "Take it steady"
                else -> "Prioritize recovery"
            },
            hrvDeltaPct = hrvDeltaPct,
            restingHrDelta = rhrDelta,
            sleepHours = sleepHours,
        )
    }

    /** Latest value from today or yesterday, paired with the mean of the 14 days before it. */
    private fun deltaFromBaseline(today: LocalDate, values: List<DailyValue>): Pair<Double, Double>? {
        val latest = values.filter { !it.date.isAfter(today) }.maxByOrNull { it.date } ?: return null
        if (latest.date.isBefore(today.minusDays(1))) return null
        val baseline = values.filter {
            it.date.isBefore(latest.date) && !it.date.isBefore(latest.date.minusDays(BASELINE_DAYS))
        }
        if (baseline.size < 3) return null
        val mean = baseline.map { it.value }.average()
        return if (mean > 0) latest.value to mean else null
    }
}
