package io.github.sjakubiak.fitboi.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// Domain models the UI renders. Everything is already converted to friendly units.

data class DayActivity(
    val date: LocalDate,
    val steps: Long,
    val distanceKm: Double,
    val caloriesKcal: Double?,
    val activeZoneMinutes: Long,
)

data class Workout(
    val name: String,
    val start: Instant,
    val duration: Duration,
    val caloriesKcal: Double?,
    val distanceKm: Double?,
    val averageHeartRate: Long?,
)

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, ASLEEP, RESTLESS }

data class SleepSegment(val stage: SleepStage, val start: Instant, val end: Instant)

data class SleepNight(
    /** The local date the person woke up on. */
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val minutesAsleep: Long,
    val minutesAwake: Long,
    val stageMinutes: Map<SleepStage, Long>,
    val segments: List<SleepSegment>,
)

data class DailyValue(val date: LocalDate, val value: Double)

data class HeartSample(val time: Instant, val avgBpm: Double, val minBpm: Double?, val maxBpm: Double?)

data class Readiness(
    /** 0–100, Fitboi's own estimate. Google's Readiness score is not exposed by the API. */
    val score: Int,
    val label: String,
    val hrvDeltaPct: Double?,
    val restingHrDelta: Double?,
    val sleepHours: Double?,
)

data class HealthSnapshot(
    val today: LocalDate,
    val activity: List<DayActivity>,
    val workouts: List<Workout>,
    val sleep: List<SleepNight>,
    val restingHeartRate: List<DailyValue>,
    val hrv: List<DailyValue>,
    val heartToday: List<HeartSample>,
    val readiness: Readiness?,
) {
    val todayActivity: DayActivity? get() = activity.firstOrNull { it.date == today }
    val lastNight: SleepNight? get() = sleep.maxByOrNull { it.end }
}

data class Goals(
    val steps: Long = 10_000,
    val activeZoneMinutes: Long = 22,
    val sleepMinutes: Long = 8 * 60,
)
