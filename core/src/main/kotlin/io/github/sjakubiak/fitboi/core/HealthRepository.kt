package io.github.sjakubiak.fitboi.core

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Loads everything the app shows in one pass and maps it into [HealthSnapshot]. */
class HealthRepository(private val api: HealthApi) {

    suspend fun loadSnapshot(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): HealthSnapshot = coroutineScope {
        val tomorrow = today.plusDays(1)
        val monthAgo = today.minusDays(HISTORY_DAYS - 1)

        val steps = async { soft { api.dailyRollUp("steps", monthAgo, tomorrow) } }
        val distance = async { soft { api.dailyRollUp("distance", monthAgo, tomorrow) } }
        val azm = async { soft { api.dailyRollUp("active-zone-minutes", monthAgo, tomorrow) } }
        // total-calories rollups are capped at 14 days.
        val calories = async { soft { api.dailyRollUp("total-calories", today.minusDays(13), tomorrow) } }
        val exercise = async {
            soft { api.list("exercise", "exercise.interval.civil_start_time >= \"$monthAgo\"") }
        }
        val sleep = async {
            soft { api.list("sleep", "sleep.interval.civil_end_time >= \"${today.minusDays(13)}T00:00:00\"") }
        }
        val rhr = async {
            soft { api.list("daily-resting-heart-rate", "daily_resting_heart_rate.date >= \"$monthAgo\"") }
        }
        val hrv = async {
            soft { api.list("daily-heart-rate-variability", "daily_heart_rate_variability.date >= \"$monthAgo\"") }
        }
        val heart = async {
            val start = today.atStartOfDay(zone).toInstant()
            soft { api.rollUp("heart-rate", start, maxOf(now, start.plusSeconds(60)), HEART_WINDOW_SECONDS) }
        }

        val activity = mergeActivity(steps.await(), distance.await(), azm.await(), calories.await())
        val sleepNights = mapSleep(sleep.await(), zone)
        val restingHr = rhr.await().mapNotNull { it.dailyRestingHeartRate }
            .map { DailyValue(it.date.toLocalDate(), it.beatsPerMinute.toDouble()) }
            .sortedBy { it.date }
        val hrvValues = hrv.await().mapNotNull { it.dailyHeartRateVariability }
            .mapNotNull { p -> p.averageHeartRateVariabilityMilliseconds?.let { DailyValue(p.date.toLocalDate(), it) } }
            .sortedBy { it.date }

        HealthSnapshot(
            today = today,
            activity = activity,
            workouts = mapWorkouts(exercise.await()),
            sleep = sleepNights,
            restingHeartRate = restingHr,
            hrv = hrvValues,
            heartToday = heart.await().mapNotNull { p ->
                p.heartRate?.beatsPerMinuteAvg?.let {
                    HeartSample(Instant.parse(p.startTime), it, p.heartRate.beatsPerMinuteMin, p.heartRate.beatsPerMinuteMax)
                }
            },
            readiness = ReadinessCalculator.compute(today, hrvValues, restingHr, sleepNights.maxByOrNull { it.end }),
        )
    }

    /** Treats "this data type isn't available" responses as empty, but lets auth and server errors through. */
    private suspend fun <T> soft(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: HealthApiException) {
        if (e.code in 400..499 && e.code != 401 && e.code != 403 && e.code != 429) emptyList() else throw e
    }

    companion object {
        const val HISTORY_DAYS = 30L
        const val HEART_WINDOW_SECONDS = 600L

        internal fun mergeActivity(
            steps: List<DailyRollupDataPoint>,
            distance: List<DailyRollupDataPoint>,
            azm: List<DailyRollupDataPoint>,
            calories: List<DailyRollupDataPoint>,
        ): List<DayActivity> {
            fun List<DailyRollupDataPoint>.byDate() = associateBy { it.civilStartTime.date.toLocalDate() }
            val s = steps.byDate()
            val d = distance.byDate()
            val a = azm.byDate()
            val c = calories.byDate()
            return (s.keys + d.keys + a.keys + c.keys).sorted().map { date ->
                val zones = a[date]?.activeZoneMinutes
                DayActivity(
                    date = date,
                    steps = s[date]?.steps?.countSum ?: 0,
                    distanceKm = (d[date]?.distance?.millimetersSum ?: 0) / 1_000_000.0,
                    caloriesKcal = c[date]?.totalCalories?.kcalSum,
                    activeZoneMinutes = zones?.let {
                        it.sumInFatBurnHeartZone + it.sumInCardioHeartZone + it.sumInPeakHeartZone
                    } ?: 0,
                )
            }
        }

        internal fun mapSleep(points: List<DataPoint>, zone: ZoneId): List<SleepNight> =
            points.mapNotNull { it.sleep }
                .filterNot { it.metadata?.nap == true }
                .map { sleep ->
                    val start = Instant.parse(sleep.interval.startTime)
                    val end = Instant.parse(sleep.interval.endTime)
                    val segments = sleep.stages.mapNotNull { stage ->
                        val type = runCatching { SleepStage.valueOf(stage.type) }.getOrNull() ?: return@mapNotNull null
                        SleepSegment(type, Instant.parse(stage.startTime), Instant.parse(stage.endTime))
                    }.sortedBy { it.start }
                    val stageMinutes = sleep.summary?.stagesSummary.orEmpty()
                        .mapNotNull { s -> runCatching { SleepStage.valueOf(s.type) }.getOrNull()?.let { it to s.minutes } }
                        .toMap()
                        .ifEmpty {
                            segments.groupBy { it.stage }
                                .mapValues { (_, list) -> list.sumOf { Duration.between(it.start, it.end).toMinutes() } }
                        }
                    val totalMinutes = Duration.between(start, end).toMinutes()
                    val awake = sleep.summary?.minutesAwake ?: (stageMinutes[SleepStage.AWAKE] ?: 0)
                    SleepNight(
                        date = end.atZone(zone).toLocalDate(),
                        start = start,
                        end = end,
                        minutesAsleep = sleep.summary?.minutesAsleep ?: (totalMinutes - awake),
                        minutesAwake = awake,
                        stageMinutes = stageMinutes,
                        segments = segments,
                    ) to (sleep.metadata?.mainSleep == true)
                }
                // One night per wake-up date: prefer the main sleep, then the longest.
                .groupBy { it.first.date }
                .map { (_, nights) ->
                    nights.maxWith(compareBy<Pair<SleepNight, Boolean>> { it.second }.thenBy { it.first.minutesAsleep }).first
                }
                .sortedBy { it.date }

        internal fun mapWorkouts(points: List<DataPoint>): List<Workout> =
            points.mapNotNull { it.exercise }.map { e ->
                val start = Instant.parse(e.interval.startTime)
                val end = Instant.parse(e.interval.endTime)
                Workout(
                    name = e.displayName ?: e.exerciseType?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                        ?: "Workout",
                    start = start,
                    duration = e.activeDuration?.let(::parseDuration) ?: Duration.between(start, end),
                    caloriesKcal = e.metricsSummary?.caloriesKcal,
                    distanceKm = e.metricsSummary?.distanceMm?.takeIf { it > 0 }?.div(1_000_000.0),
                    averageHeartRate = e.metricsSummary?.averageHeartRateBeatsPerMinute,
                )
            }.sortedByDescending { it.start }

        /** Parses protobuf JSON durations such as "900s" or "1.5s". */
        internal fun parseDuration(value: String): Duration? =
            value.removeSuffix("s").toDoubleOrNull()?.let { Duration.ofMillis((it * 1000).toLong()) }
    }
}
