package com.example.guider

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.guider.ui.GuiderApp
import com.example.guider.ui.GuiderDestination
import com.example.guider.ui.theme.AppTheme
import com.example.guider.notifications.WakeUpReceiver
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val destinationRequest = mutableStateOf<GuiderDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val guiderApplication = application as GuiderApplication
                val dailyTasksStatusFlow = remember(guiderApplication) {
                    guiderApplication.featureStatuses
                        .map { statuses -> statuses.getValue(AppFeature.DAILY_TASKS) }
                        .distinctUntilChanged()
                }
                val dailyTasksStatus by dailyTasksStatusFlow.collectAsStateWithLifecycle(
                    initialValue = guiderApplication.featureStatus(AppFeature.DAILY_TASKS),
                )
                if (dailyTasksStatus == FeatureLoadStatus.READY) {
                    GuiderApp(
                        destinationRequest = destinationRequest.value,
                        onDestinationRequestConsumed = { destinationRequest.value = null },
                    )
                } else {
                    GuiderLoadingScreen(
                        failed = dailyTasksStatus == FeatureLoadStatus.FAILED,
                        onRetry = { guiderApplication.requestFeature(AppFeature.DAILY_TASKS) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != WakeUpReceiver.ACTION_CONFIRM_WAKE_UP) return
        val guiderApplication = application as GuiderApplication
        intent.action = null
        lifecycleScope.launch {
            runCatching {
                guiderApplication.awaitFeature(AppFeature.SLEEP)
                guiderApplication.sleepRepository.finishHibernation(System.currentTimeMillis())
                guiderApplication.hibernationNotificationManager.cancelActiveSession()
                guiderApplication.hibernationPromptScheduler.cancel()
                destinationRequest.value = GuiderDestination.SLEEP
            }
        }
    }
}

@Composable
private fun GuiderLoadingScreen(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (!failed) CircularProgressIndicator()
        Text(
            text = if (failed) "Guider couldn't load" else "Loading Guider",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (failed) {
            TextButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}
