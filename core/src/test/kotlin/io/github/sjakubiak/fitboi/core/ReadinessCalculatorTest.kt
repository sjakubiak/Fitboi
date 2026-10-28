package io.github.sjakubiak.fitboi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReadinessCalculatorTest {
    private val today = LocalDate.of(2026, 10, 27)

    private fun series(vararg values: Double) =
        values.mapIndexed { i, v -> DailyValue(today.minusDays((values.size - 1 - i).toLong()), v) }

    private fun night(hours: Double) = SleepNight(
        date = today, start = Instant.EPOCH, end = Instant.EPOCH,
        minutesAsleep = (hours * 60).toLong(), minutesAwake = 0, stageMinutes = emptyMap(), segments = emptyList(),
    )

    @Test
    fun `no data gives no score`() {
        assertNull(ReadinessCalculator.compute(today, emptyList(), emptyList(), null))
    }

    @Test
    fun `higher hrv and lower resting hr than baseline scores high`() {
        val r = ReadinessCalculator.compute(
            today,
            hrv = series(40.0, 40.0, 40.0, 40.0, 50.0),
            restingHr = series(60.0, 60.0, 60.0, 60.0, 57.0),
            lastNight = night(8.0),
        )!!
        assertEquals(25.0, r.hrvDeltaPct!!, 0.001)
        assertEquals(-3.0, r.restingHrDelta!!, 0.001)
        assertEquals(100, r.score)
        assertEquals("Ready to push", r.label)
    }

    @Test
    fun `short sleep and suppressed hrv scores low`() {
        val r = ReadinessCalculator.compute(
            today,
            hrv = series(50.0, 50.0, 50.0, 50.0, 35.0),
            restingHr = series(55.0, 55.0, 55.0, 55.0, 59.0),
            lastNight = night(5.0),
        )!!
        assertTrue(r.score < 40)
        assertEquals("Prioritize recovery", r.label)
    }

    @Test
    fun `stale values are ignored`() {
        val old = listOf(DailyValue(today.minusDays(10), 40.0), DailyValue(today.minusDays(9), 40.0))
        val r = ReadinessCalculator.compute(today, old, old, night(7.0))!!
        assertNull(r.hrvDeltaPct)
        assertEquals(60, r.score)
    }
}
