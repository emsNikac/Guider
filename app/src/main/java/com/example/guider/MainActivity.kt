package com.example.guider

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.guider.ui.GuiderApp
import com.example.guider.ui.GuiderDestination
import com.example.guider.ui.theme.AppTheme
import com.example.guider.notifications.WakeUpReceiver
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val destinationRequest = mutableStateOf<GuiderDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                GuiderApp(
                    destinationRequest = destinationRequest.value,
                    onDestinationRequestConsumed = { destinationRequest.value = null },
                )
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
        guiderApplication.sleepRepository.finishHibernation(System.currentTimeMillis())
        guiderApplication.hibernationNotificationManager.cancelActiveSession()
        guiderApplication.hibernationPromptScheduler.cancel()
        destinationRequest.value = GuiderDestination.SLEEP
        intent.action = null
    }
}
