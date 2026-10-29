package io.github.sjakubiak.fitboi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sjakubiak.fitboi.core.Goals
import io.github.sjakubiak.fitboi.core.HealthSnapshot
import io.github.sjakubiak.fitboi.ui.components.ProgressRing
import io.github.sjakubiak.fitboi.ui.screens.ActivityScreen
import io.github.sjakubiak.fitboi.ui.screens.HeartScreen
import io.github.sjakubiak.fitboi.ui.screens.SleepScreen
import io.github.sjakubiak.fitboi.ui.screens.Tab
import io.github.sjakubiak.fitboi.ui.screens.TodayScreen
import io.github.sjakubiak.fitboi.ui.theme.FitboiTheme
import io.github.sjakubiak.fitboi.ui.theme.HealthColors

class MainActivity : ComponentActivity() {
    private val viewModel: HealthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitboiTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FitboiApp(viewModel) }
            }
        }
    }
}

@Composable
private fun FitboiApp(viewModel: HealthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.data)
    }

    when (val s = state) {
        UiState.SignedOut -> ConnectScreen(onConnect = viewModel::connect)
        UiState.Loading -> CenteredBox { CircularProgressIndicator() }
        is UiState.NeedsConsent -> {
            LaunchedEffect(s.pendingIntent) {
                consentLauncher.launch(IntentSenderRequest.Builder(s.pendingIntent.intentSender).build())
            }
            CenteredBox { CircularProgressIndicator() }
        }
        is UiState.Error -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = viewModel::refresh) { Text("Try again") }
                OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
            }
        }
        is UiState.Ready -> HealthHome(s.snapshot, s.refreshing, viewModel::refresh, viewModel::disconnect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthHome(snapshot: HealthSnapshot, refreshing: Boolean, onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    val goals = Goals()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fitboi") },
                actions = {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect Google Health")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon(), contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            when (tab) {
                Tab.Today -> TodayScreen(snapshot, goals, onOpen = { tab = it })
                Tab.Activity -> ActivityScreen(snapshot, goals)
                Tab.Sleep -> SleepScreen(snapshot, goals)
                Tab.Heart -> HeartScreen(snapshot)
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Today -> Icons.Filled.WbSunny
    Tab.Activity -> Icons.AutoMirrored.Filled.DirectionsWalk
    Tab.Sleep -> Icons.Filled.Bedtime
    Tab.Heart -> Icons.Filled.Favorite
}

@Composable
private fun ConnectScreen(onConnect: () -> Unit) {
    CenteredBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(0.72f, HealthColors.Steps, size = 140.dp, stroke = 14.dp) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = HealthColors.Heart)
            }
            Text("Fitboi", style = MaterialTheme.typography.displaySmall)
            Text(
                "See your Fitbit Air activity, sleep and heart data. Fitboi reads it from Google Health with your permission.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConnect) { Text("Connect Google Health") }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}
