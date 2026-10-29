package io.github.sjakubiak.fitboi.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.github.sjakubiak.fitboi.core.AccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Thrown when Google needs the person to (re)approve access; the UI launches [pendingIntent]. */
class ConsentRequiredException(val pendingIntent: PendingIntent) : Exception("Google Health access needs approval")

/**
 * Gets Google Health API access tokens through Google Play services' AuthorizationClient.
 * Once access is granted, later calls return fresh tokens silently, so there is no refresh token to store.
 */
class GoogleHealthAuth(context: Context) : AccessTokenProvider {
    private val appContext = context.applicationContext
    private val client = Identity.getAuthorizationClient(appContext)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES.map(::Scope))
        .build()

    @Volatile private var cached: CachedToken? = null

    /** Returns a token, or throws [ConsentRequiredException] if the person must approve access first. */
    override suspend fun accessToken(): String {
        cached?.takeIf { it.isFresh() }?.let { return it.token }
        val result = client.authorize(request).await()
        if (result.hasResolution()) throw ConsentRequiredException(result.pendingIntent!!)
        return remember(result)
    }

    /** Completes the consent screen launched from [ConsentRequiredException.pendingIntent]. */
    fun completeConsent(data: Intent?): String =
        remember(client.getAuthorizationResultFromIntent(data))

    /** Drops the cached token so the next call fetches a new one (e.g. after a 401). */
    suspend fun invalidate() {
        val token = cached?.token ?: return
        cached = null
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(appContext, token) } }
    }

    private fun remember(result: AuthorizationResult): String {
        val token = checkNotNull(result.accessToken) { "Google returned no access token" }
        cached = CachedToken(token, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
        return token
    }

    private data class CachedToken(val token: String, val expiresAtMs: Long) {
        fun isFresh() = System.currentTimeMillis() < expiresAtMs
    }

    companion object {
        // Access tokens last about an hour; refresh a little early.
        private const val TOKEN_LIFETIME_MS = 50 * 60 * 1000L

        val SCOPES = listOf(
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
        )
    }
}
