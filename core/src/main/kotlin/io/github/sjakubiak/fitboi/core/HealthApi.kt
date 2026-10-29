package io.github.sjakubiak.fitboi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Supplies a fresh OAuth access token for the Google Health API scopes. */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String
}

class HealthApiException(val code: Int, message: String) : IOException("Google Health API $code: $message")

/** Thin client for https://health.googleapis.com/v4, scoped to the signed-in user (`users/me`). */
class HealthApi(
    private val tokens: AccessTokenProvider,
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = "https://health.googleapis.com/v4/".toHttpUrl(),
) {
    /** Sums a data type per local calendar day for `[start, endExclusive)`. Max range is 90 days (14 for heart rate and calories). */
    suspend fun dailyRollUp(dataType: String, start: LocalDate, endExclusive: LocalDate): List<DailyRollupDataPoint> {
        val request = DailyRollUpRequest(
            range = CivilTimeInterval(CivilDateTime(ApiDate.of(start)), CivilDateTime(ApiDate.of(endExclusive))),
            pageSize = 1000,
        )
        return post<DailyRollUpResponse>("users/me/dataTypes/$dataType/dataPoints:dailyRollUp", json.encodeToString(request))
            .rollupDataPoints
    }

    /** Aggregates a data type into fixed physical-time windows, e.g. heart rate every 10 minutes. */
    suspend fun rollUp(dataType: String, start: Instant, end: Instant, windowSeconds: Long): List<RollupDataPoint> {
        val request = RollUpRequest(
            range = PhysicalInterval(start.toString(), end.toString()),
            windowSize = "${windowSeconds}s",
            pageSize = 2000,
        )
        return post<RollUpResponse>("users/me/dataTypes/$dataType/dataPoints:rollUp", json.encodeToString(request))
            .rollupDataPoints
    }

    /** Lists raw data points matching [filter], following page tokens. */
    suspend fun list(dataType: String, filter: String, maxPages: Int = 5): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        var pageToken: String? = null
        var pages = 0
        do {
            val url = baseUrl.newBuilder()
                .addPathSegments("users/me/dataTypes/$dataType/dataPoints")
                .addQueryParameter("filter", filter)
                .addQueryParameter("pageSize", "100")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = execute<ListDataPointsResponse>(Request.Builder().url(url).get())
            points += page.dataPoints
            pageToken = page.nextPageToken?.takeIf { it.isNotEmpty() }
            pages++
        } while (pageToken != null && pages < maxPages)
        return points
    }

    private suspend inline fun <reified T> post(path: String, body: String): T =
        execute(Request.Builder().url(baseUrl.resolve(path)!!).post(body.toRequestBody(JSON_MEDIA)))

    private suspend inline fun <reified T> execute(builder: Request.Builder): T {
        val token = tokens.accessToken()
        val text = withContext(Dispatchers.IO) {
            http.newCall(
                builder.header("Authorization", "Bearer $token").header("Accept", "application/json").build()
            ).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HealthApiException(response.code, payload.take(500))
                payload
            }
        }
        return json.decodeFromString(text)
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}
