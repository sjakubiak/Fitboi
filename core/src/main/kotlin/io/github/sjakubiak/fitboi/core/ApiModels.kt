package io.github.sjakubiak.fitboi.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

// Wire models for the subset of the Google Health API v4 that Fitboi reads.
// int64 fields arrive as JSON strings; the shared Json instance is lenient so they decode into Long.

@Serializable
data class ApiDate(val year: Int, val month: Int, val day: Int) {
    fun toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    companion object {
        fun of(date: LocalDate) = ApiDate(date.year, date.monthValue, date.dayOfMonth)
    }
}

@Serializable
data class CivilDateTime(val date: ApiDate)

@Serializable
data class CivilTimeInterval(val start: CivilDateTime, val end: CivilDateTime)

@Serializable
data class PhysicalInterval(val startTime: String, val endTime: String)

@Serializable
data class DailyRollUpRequest(
    val range: CivilTimeInterval,
    val windowSizeDays: Int = 1,
    val pageSize: Int? = null,
)

@Serializable
data class RollUpRequest(
    val range: PhysicalInterval,
    val windowSize: String,
    val pageSize: Int? = null,
)

@Serializable
data class DailyRollUpResponse(
    val rollupDataPoints: List<DailyRollupDataPoint> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class RollUpResponse(
    val rollupDataPoints: List<RollupDataPoint> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DailyRollupDataPoint(
    val civilStartTime: CivilDateTime,
    val steps: StepsRollup? = null,
    val distance: DistanceRollup? = null,
    val totalCalories: CaloriesRollup? = null,
    val activeZoneMinutes: ActiveZoneMinutesRollup? = null,
    val heartRate: HeartRateRollup? = null,
)

@Serializable
data class RollupDataPoint(
    val startTime: String,
    val endTime: String,
    val heartRate: HeartRateRollup? = null,
)

@Serializable
data class StepsRollup(val countSum: Long = 0)

@Serializable
data class DistanceRollup(val millimetersSum: Long = 0)

@Serializable
data class CaloriesRollup(val kcalSum: Double = 0.0)

@Serializable
data class ActiveZoneMinutesRollup(
    val sumInFatBurnHeartZone: Long = 0,
    val sumInCardioHeartZone: Long = 0,
    val sumInPeakHeartZone: Long = 0,
)

@Serializable
data class HeartRateRollup(
    val beatsPerMinuteAvg: Double? = null,
    val beatsPerMinuteMin: Double? = null,
    val beatsPerMinuteMax: Double? = null,
)

@Serializable
data class ListDataPointsResponse(
    val dataPoints: List<DataPoint> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DataPoint(
    val name: String? = null,
    val sleep: SleepPoint? = null,
    val exercise: ExercisePoint? = null,
    val dailyRestingHeartRate: DailyRestingHeartRatePoint? = null,
    val dailyHeartRateVariability: DailyHeartRateVariabilityPoint? = null,
)

@Serializable
data class SessionInterval(
    val startTime: String,
    val endTime: String,
)

@Serializable
data class SleepPoint(
    val interval: SessionInterval,
    val stages: List<SleepStagePoint> = emptyList(),
    val summary: SleepSummaryPoint? = null,
    val metadata: SleepMetadataPoint? = null,
)

@Serializable
data class SleepStagePoint(val startTime: String, val endTime: String, val type: String)

@Serializable
data class SleepSummaryPoint(
    val minutesAsleep: Long? = null,
    val minutesAwake: Long? = null,
    val minutesInSleepPeriod: Long? = null,
    val stagesSummary: List<StageSummaryPoint> = emptyList(),
)

@Serializable
data class StageSummaryPoint(val type: String, val minutes: Long = 0)

@Serializable
data class SleepMetadataPoint(val mainSleep: Boolean = false, val nap: Boolean = false)

@Serializable
data class ExercisePoint(
    val interval: SessionInterval,
    val exerciseType: String? = null,
    val displayName: String? = null,
    val activeDuration: String? = null,
    val metricsSummary: ExerciseMetrics? = null,
)

@Serializable
data class ExerciseMetrics(
    val caloriesKcal: Double? = null,
    val distanceMillimeters: Double? = null,
    // Misspelled variant seen in Google's own codelab responses.
    val distanceMillimiters: Double? = null,
    val steps: Long? = null,
    val averageHeartRateBeatsPerMinute: Long? = null,
    val activeZoneMinutes: Long? = null,
) {
    val distanceMm: Double? get() = distanceMillimeters ?: distanceMillimiters
}

@Serializable
data class DailyRestingHeartRatePoint(val date: ApiDate, val beatsPerMinute: Long)

@Serializable
data class DailyHeartRateVariabilityPoint(
    val date: ApiDate,
    val averageHeartRateVariabilityMilliseconds: Double? = null,
)
