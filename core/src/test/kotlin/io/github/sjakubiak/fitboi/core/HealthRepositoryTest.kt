package io.github.sjakubiak.fitboi.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthRepositoryTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 27)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val path = request.requestUrl!!.encodedPath
                val type = path.substringAfter("dataTypes/").substringBefore("/")
                val fixture = when {
                    path.endsWith(":dailyRollUp") -> "daily-$type.json"
                    path.endsWith(":rollUp") -> "rollup-$type.json"
                    else -> "list-$type.json"
                }
                val body = javaClass.classLoader.getResource(fixture)?.readText()
                    ?: return MockResponse().setResponseCode(400).setBody("{\"error\":\"unsupported\"}")
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = HealthRepository(HealthApi({ "test-token" }, baseUrl = server.url("/v4/")))

    @Test
    fun `loads a full snapshot from the API`() = runTest {
        val snapshot = repository().loadSnapshot(today, zone, Instant.parse("2026-10-27T18:00:00Z"))

        val todayActivity = snapshot.todayActivity!!
        assertEquals(8421L, todayActivity.steps)
        assertEquals(6.2, todayActivity.distanceKm, 0.001)
        assertEquals(2140.5, todayActivity.caloriesKcal!!, 0.001)
        assertEquals(31L, todayActivity.activeZoneMinutes)

        val night = snapshot.lastNight!!
        assertEquals(today, night.date)
        assertEquals(412L, night.minutesAsleep)
        assertEquals(68L, night.stageMinutes[SleepStage.DEEP])
        assertEquals(4, night.segments.size)

        assertEquals(listOf(58.0, 57.0, 59.0, 56.0), snapshot.restingHeartRate.map { it.value })
        assertEquals(4, snapshot.hrv.size)
        assertEquals(2, snapshot.heartToday.size)
        assertEquals("Walk", snapshot.workouts.single().name)
        assertEquals(1.609, snapshot.workouts.single().distanceKm!!, 0.001)
        assertNotNull(snapshot.readiness)

        requests.forEach { assertEquals("Bearer test-token", it.getHeader("Authorization")) }
    }

    @Test
    fun `daily rollup sends a civil date range`() = runTest {
        repository().loadSnapshot(today, zone, Instant.parse("2026-10-27T18:00:00Z"))
        val steps = requests.first { it.path!!.contains("steps/dataPoints:dailyRollUp") }
        val body = steps.body.readUtf8()
        assertTrue(body, body.contains("\"start\":{\"date\":{\"year\":2026,\"month\":9,\"day\":28}}"))
        assertTrue(body, body.contains("\"end\":{\"date\":{\"year\":2026,\"month\":10,\"day\":28}}"))

        val sleep = requests.first { it.path!!.contains("sleep/dataPoints") }
        assertEquals("sleep.interval.civil_end_time >= \"2026-10-14T00:00:00\"", sleep.requestUrl!!.queryParameter("filter"))
    }

    @Test
    fun `missing data types come back empty instead of failing`() = runTest {
        // There is no fixture for workouts in this test's server, so it answers 400.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(400)
        }
        val snapshot = repository().loadSnapshot(today, zone, Instant.parse("2026-10-27T18:00:00Z"))
        assertTrue(snapshot.activity.isEmpty())
        assertEquals(null, snapshot.readiness)
    }

    @Test(expected = HealthApiException::class)
    fun `auth errors propagate`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(401)
        }
        repository().loadSnapshot(today, zone, Instant.parse("2026-10-27T18:00:00Z"))
    }

    @Test
    fun `parses protobuf durations`() {
        assertEquals(900L, HealthRepository.parseDuration("900s")!!.seconds)
        assertEquals(1500L, HealthRepository.parseDuration("1.5s")!!.toMillis())
    }
}
