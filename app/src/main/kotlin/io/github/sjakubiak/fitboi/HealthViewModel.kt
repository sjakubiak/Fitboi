package io.github.sjakubiak.fitboi

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sjakubiak.fitboi.auth.ConsentRequiredException
import io.github.sjakubiak.fitboi.auth.GoogleHealthAuth
import io.github.sjakubiak.fitboi.core.HealthApi
import io.github.sjakubiak.fitboi.core.HealthApiException
import io.github.sjakubiak.fitboi.core.HealthRepository
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState {
    data object SignedOut : UiState
    data object Loading : UiState
    data class NeedsConsent(val pendingIntent: PendingIntent) : UiState
    data class Ready(val snapshot: HealthSnapshot, val refreshing: Boolean = false) : UiState
    data class Error(val message: String) : UiState
}

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("fitboi", Application.MODE_PRIVATE)
    private val auth = GoogleHealthAuth(application)
    private val repository = HealthRepository(HealthApi(auth))

    private val _state = MutableStateFlow<UiState>(if (connected) UiState.Loading else UiState.SignedOut)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var connected: Boolean
        get() = prefs.getBoolean(KEY_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTED, value).apply()

    init {
        if (connected) refresh()
    }

    fun connect() {
        _state.value = UiState.Loading
        refresh()
    }

    fun onConsentResult(data: Intent?) {
        runCatching { auth.completeConsent(data) }
            .onSuccess { refresh() }
            .onFailure { _state.value = UiState.SignedOut }
    }

    fun disconnect() {
        connected = false
        viewModelScope.launch { auth.invalidate() }
        _state.value = UiState.SignedOut
    }

    fun refresh() {
        _state.update { if (it is UiState.Ready) it.copy(refreshing = true) else UiState.Loading }
        viewModelScope.launch {
            _state.value = try {
                UiState.Ready(loadWithRetry()).also { connected = true }
            } catch (e: ConsentRequiredException) {
                UiState.NeedsConsent(e.pendingIntent)
            } catch (e: HealthApiException) {
                UiState.Error(describe(e))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Something went wrong loading your data.")
            }
        }
    }

    /** A 401 usually means a stale cached token, so clear it and try once more. */
    private suspend fun loadWithRetry(): HealthSnapshot = try {
        repository.loadSnapshot()
    } catch (e: HealthApiException) {
        if (e.code != 401) throw e
        auth.invalidate()
        repository.loadSnapshot()
    }

    private fun describe(e: HealthApiException) = when (e.code) {
        401, 403 -> "Google didn't allow access to your health data. Check that your account is a test user " +
            "on the OAuth consent screen and that you've signed in to the Google Health app once."
        429 -> "Too many requests to Google Health. Wait a minute and pull to refresh."
        else -> "Google Health returned an error (${e.code}). Pull to refresh to try again."
    }

    private companion object {
        const val KEY_CONNECTED = "connected"
    }
}
